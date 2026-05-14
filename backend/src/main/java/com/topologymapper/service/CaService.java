package com.topologymapper.service;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class CaService {

    static {
        if (Security.getProvider("BC") == null)
            Security.addProvider(new BouncyCastleProvider());
    }

    private static final String ALGO     = "RSA";
    private static final String SIGN_ALG = "SHA256WithRSA";
    private static final Path   DATA_DIR = Path.of("data", "dns");

    private KeyPair       caKeyPair;
    private X509Certificate caCert;

    /** Server private key — reused for all per-hostname certs to avoid endless key gen */
    private KeyPair serverKeyPair;

    /** hostname → signed cert (in-memory cache) */
    private final Map<String, X509Certificate> certCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws Exception {
        Files.createDirectories(DATA_DIR);
        Path caKeyPath  = DATA_DIR.resolve("ca.key");
        Path caCertPath = DATA_DIR.resolve("ca.crt");

        if (Files.exists(caKeyPath) && Files.exists(caCertPath)) {
            caKeyPair = loadKeyPair(caKeyPath);
            caCert    = loadCert(caCertPath);
            log.info("Loaded existing CA cert from {}", caCertPath);
        } else {
            caKeyPair = generateKeyPair();
            caCert    = buildCaCert(caKeyPair);
            saveKeyPair(caKeyPair, caKeyPath);
            saveCert(caCert, caCertPath);
            log.info("Generated new CA cert — install {} on your phone", caCertPath.toAbsolutePath());
        }

        serverKeyPair = generateKeyPair();
        log.info("CA ready. CN={}", caCert.getSubjectX500Principal().getName());
    }

    /** Returns the CA cert as DER bytes (for download / QR code link) */
    public byte[] getCaCertDer() throws Exception {
        return caCert.getEncoded();
    }

    /**
     * Returns a cert chain [serverCert, caCert] for the given hostname.
     * Cert is generated on first call and cached forever (session-scoped).
     */
    public X509Certificate[] getCertChain(String hostname) {
        X509Certificate serverCert = certCache.computeIfAbsent(hostname, h -> {
            try { return signServerCert(h); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        return new X509Certificate[]{serverCert, caCert};
    }

    public PrivateKey getServerPrivateKey() {
        return serverKeyPair.getPrivate();
    }

    // ---- Cert builders -------------------------------------------------------

    private X509Certificate buildCaCert(KeyPair kp) throws Exception {
        X500Name name = new X500Name("CN=Topology Mapper CA,O=Local Network Lab,C=KE");
        Date from = new Date();
        Date to   = new Date(from.getTime() + 10L * 365 * 24 * 60 * 60 * 1000);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            name, BigInteger.ONE, from, to, name, kp.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
            new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature));
        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALG).build(kp.getPrivate());
        return new JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(builder.build(signer));
    }

    private X509Certificate signServerCert(String hostname) throws Exception {
        X500Name issuer  = new X500Name(caCert.getSubjectX500Principal().getName());
        X500Name subject = new X500Name("CN=" + hostname);
        Date from = new Date();
        Date to   = new Date(from.getTime() + 365L * 24 * 60 * 60 * 1000);
        BigInteger serial = new BigInteger(64, new SecureRandom());

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            issuer, serial, from, to, subject, serverKeyPair.getPublic());

        // SAN: cover exact hostname + wildcard subdomain
        GeneralName[] sans = {
            new GeneralName(GeneralName.dNSName, hostname),
            new GeneralName(GeneralName.dNSName, "*." + hostname),
        };
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(sans));
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true,
            new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.extendedKeyUsage, false,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

        ContentSigner signer = new JcaContentSignerBuilder(SIGN_ALG).build(caKeyPair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(builder.build(signer));
        log.debug("Signed cert for hostname: {}", hostname);
        return cert;
    }

    // ---- Key / cert I/O ----------------------------------------------------

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGO, "BC");
        kpg.initialize(2048, new SecureRandom());
        return kpg.generateKeyPair();
    }

    private void saveKeyPair(KeyPair kp, Path path) throws Exception {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            oos.writeObject(kp.getPrivate());
            oos.writeObject(kp.getPublic());
        }
    }

    private KeyPair loadKeyPair(Path path) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            PrivateKey priv = (PrivateKey) ois.readObject();
            PublicKey  pub  = (PublicKey)  ois.readObject();
            return new KeyPair(pub, priv);
        }
    }

    private void saveCert(X509Certificate cert, Path path) throws Exception {
        Files.write(path, cert.getEncoded());
    }

    private X509Certificate loadCert(Path path) throws Exception {
        byte[] der = Files.readAllBytes(path);
        return (X509Certificate) java.security.cert.CertificateFactory
            .getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }
}
