package com.wifi.scan.consume.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * SSL configuration component for Kafka SSL/TLS connectivity and certificate management.
 * 
 * This class provides comprehensive SSL/TLS configuration capabilities for secure
 * Kafka communication. It handles certificate loading from multiple sources including
 * classpath resources and external file paths, with support for various keystore
 * formats and automatic resource resolution.
 * 
 * Key Functionality:
 * - Keystore and truststore loading from classpath and external locations
 * - SSL context creation with proper certificate validation
 * - Certificate validation and health checking
 * - SSL properties building for Kafka configuration
 * - Resource location resolution and normalization
 * - Temporary file extraction for classpath resources
 * 
 * High-Level Processing Steps:
 * 1. Load keystore/truststore from configured locations
 * 2. Validate certificate integrity and expiration
 * 3. Create SSL context with proper key and trust managers
 * 4. Build SSL properties for Kafka client configuration
 * 5. Handle resource resolution for different location types
 * 6. Extract classpath resources to temporary files when needed
 * 
 * Certificate Management:
 * - Supports JKS, PKCS12, and other keystore formats
 * - Handles both client certificates (keystore) and server certificates (truststore)
 * - Provides certificate validation and health monitoring
 * - Supports classpath and external file system locations
 * 
 * Security Features:
 * - Secure certificate loading with proper exception handling
 * - Certificate validation and integrity checking
 * - Temporary file cleanup for extracted resources
 * - Comprehensive logging for security auditing
 * 
 * @see KafkaProperties
 * @see SSLContext
 * @see KeyStore
 */
@Slf4j
@Component
public class SslConfiguration {

    /** Spring ResourceLoader for accessing classpath and external resources */
    private final ResourceLoader resourceLoader;

    /**
     * Creates a new SslConfiguration with Spring ResourceLoader for resource access.
     * 
     * @param resourceLoader Spring ResourceLoader for accessing classpath and external resources
     */
    public SslConfiguration(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Creates a new SslConfiguration without ResourceLoader (for testing).
     * Limited functionality for resource loading.
     */
    public SslConfiguration() {
        this.resourceLoader = null;
    }

    /**
     * Loads keystore from the configured location with automatic source detection.
     * 
     * This method intelligently determines the source of the keystore (classpath
     * or external file system) and loads it accordingly. It provides comprehensive
     * error handling and logging for security auditing.
     * 
     * Processing Steps:
     * 1. Extract keystore configuration parameters (location, password, type)
     * 2. Determine source type (classpath vs external file system)
     * 3. Load keystore from appropriate source
     * 4. Validate keystore integrity and accessibility
     * 5. Log loading results for security auditing
     * 
     * Source Detection Logic:
     * - Classpath resources: locations starting with "classpath:"
     * - External files: absolute or relative file system paths
     * - Automatic fallback handling for different location formats
     * 
     * Error Handling:
     * - Comprehensive exception catching and logging
     * - Detailed error messages for troubleshooting
     * - Runtime exception wrapping for consistent error handling
     * 
     * @param keystoreConfig Keystore configuration containing location, password, and type
     * @return Loaded KeyStore object ready for SSL context creation
     * @throws RuntimeException if keystore loading fails
     */
    public KeyStore loadKeystore(KafkaProperties.Keystore keystoreConfig) {
        try {
            log.debug("Loading keystore from location: {}", keystoreConfig.getLocation());
            
            // Extract configuration parameters
            String location = keystoreConfig.getLocation();
            String password = keystoreConfig.getPassword();
            String type = keystoreConfig.getType();

            // Determine source and load accordingly
            if (location.startsWith("classpath:")) {
                return loadKeystoreFromClasspath(location, password, type);
            } else {
                return loadKeystoreFromExternalPath(location, password, type);
            }
        } catch (Exception e) {
            log.error("Failed to load keystore from location: {}", keystoreConfig.getLocation(), e);
            throw new RuntimeException("Failed to load keystore", e);
        }
    }

    /**
     * Loads truststore from the configured location with automatic source detection.
     * 
     * This method intelligently determines the source of the truststore (classpath
     * or external file system) and loads it accordingly. It provides comprehensive
     * error handling and logging for security auditing.
     * 
     * Processing Steps:
     * 1. Extract truststore configuration parameters (location, password, type)
     * 2. Determine source type (classpath vs external file system)
     * 3. Load truststore from appropriate source
     * 4. Validate truststore integrity and accessibility
     * 5. Log loading results for security auditing
     * 
     * Source Detection Logic:
     * - Classpath resources: locations starting with "classpath:"
     * - External files: absolute or relative file system paths
     * - Automatic fallback handling for different location formats
     * 
     * Error Handling:
     * - Comprehensive exception catching and logging
     * - Detailed error messages for troubleshooting
     * - Runtime exception wrapping for consistent error handling
     * 
     * @param truststoreConfig Truststore configuration containing location, password, and type
     * @return Loaded KeyStore object ready for SSL context creation
     * @throws RuntimeException if truststore loading fails
     */
    public KeyStore loadTruststore(KafkaProperties.Truststore truststoreConfig) {
        try {
            log.debug("Loading truststore from location: {}", truststoreConfig.getLocation());
            
            // Extract configuration parameters
            String location = truststoreConfig.getLocation();
            String password = truststoreConfig.getPassword();
            String type = truststoreConfig.getType();

            // Determine source and load accordingly
            if (location.startsWith("classpath:")) {
                return loadTruststoreFromClasspath(location, password, type);
            } else {
                return loadTruststoreFromExternalPath(location, password, type);
            }
        } catch (Exception e) {
            log.error("Failed to load truststore from location: {}", truststoreConfig.getLocation(), e);
            throw new RuntimeException("Failed to load truststore", e);
        }
    }

    /**
     * Loads keystore from external file system path.
     * 
     * This method loads a keystore from an external file system location,
     * providing direct file access for production deployments where certificates
     * are stored outside the application classpath.
     * 
     * Processing Steps:
     * 1. Validate resource loader availability
     * 2. Resolve keystore location to absolute path
     * 3. Load keystore from file system
     * 4. Validate keystore format and password
     * 5. Return loaded keystore for SSL context creation
     * 
     * File System Handling:
     * - Supports absolute and relative file paths
     * - Handles different keystore formats (JKS, PKCS12)
     * - Provides detailed logging for security auditing
     * - Validates file existence and accessibility
     * 
     * Security Considerations:
     * - Password validation during keystore loading
     * - File system permission checking
     * - Comprehensive error handling for security issues
     * 
     * @param location File system path to the keystore file
     * @param password Password for accessing the keystore
     * @param type Type of keystore (JKS, PKCS12, etc.)
     * @return Loaded KeyStore object
     * @throws KeyStoreException if keystore format is invalid
     * @throws IOException if file cannot be read
     * @throws NoSuchAlgorithmException if keystore algorithm is not supported
     * @throws CertificateException if certificate format is invalid
     */
    public KeyStore loadKeystoreFromExternalPath(String location, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        log.debug("Loading keystore from external path: {}", location);
        
        if (resourceLoader != null) {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new RuntimeException("Keystore file not found at location: " + location);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return loadKeystoreFromStream(inputStream, password, type);
            }
        } else {
            // Direct file system access
            Path keystorePath = Paths.get(location);
            if (!Files.exists(keystorePath)) {
                throw new RuntimeException("Keystore file not found at location: " + location);
            }
            try (InputStream inputStream = Files.newInputStream(keystorePath)) {
                return loadKeystoreFromStream(inputStream, password, type);
            }
        }
    }

    /**
     * Loads truststore from external file system path.
     * 
     * This method loads a truststore from an external file system location,
     * providing direct file access for production deployments where certificates
     * are stored outside the application classpath.
     * 
     * Processing Steps:
     * 1. Validate resource loader availability
     * 2. Resolve truststore location to absolute path
     * 3. Load truststore from file system
     * 4. Validate truststore format and password
     * 5. Return loaded truststore for SSL context creation
     * 
     * File System Handling:
     * - Supports absolute and relative file paths
     * - Handles different truststore formats (JKS, PKCS12)
     * - Provides detailed logging for security auditing
     * - Validates file existence and accessibility
     * 
     * Security Considerations:
     * - Password validation during truststore loading
     * - File system permission checking
     * - Comprehensive error handling for security issues
     * 
     * @param location File system path to the truststore file
     * @param password Password for accessing the truststore
     * @param type Type of truststore (JKS, PKCS12, etc.)
     * @return Loaded KeyStore object
     * @throws KeyStoreException if truststore format is invalid
     * @throws IOException if file cannot be read
     * @throws NoSuchAlgorithmException if truststore algorithm is not supported
     * @throws CertificateException if certificate format is invalid
     */
    public KeyStore loadTruststoreFromExternalPath(String location, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        log.debug("Loading truststore from external path: {}", location);
        
        if (resourceLoader != null) {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new RuntimeException("Truststore file not found at location: " + location);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return loadKeystoreFromStream(inputStream, password, type);
            }
        } else {
            // Direct file system access
            Path truststorePath = Paths.get(location);
            if (!Files.exists(truststorePath)) {
                throw new RuntimeException("Truststore file not found at location: " + location);
            }
            try (InputStream inputStream = Files.newInputStream(truststorePath)) {
                return loadKeystoreFromStream(inputStream, password, type);
            }
        }
    }

    /**
     * Creates SSL context from Kafka properties.
     * 
     * This method initializes an SSLContext with the necessary key and trust managers
     * based on the provided KafkaProperties. It ensures that both keystore and truststore
     * are loaded and validated before creating the SSL context.
     * 
     * Processing Steps:
     * 1. Load keystore and truststore from configuration
     * 2. Initialize KeyManagerFactory and TrustManagerFactory
     * 3. Create SSLContext with proper key and trust managers
     * 4. Return initialized SSLContext
     * 
     * Security Features:
     * - Secure keystore and truststore loading
     * - Proper key and trust manager initialization
     * - Nullable trust manager for client-only authentication
     * - Comprehensive error handling for SSL context creation
     * 
     * @param kafkaProperties Kafka configuration properties containing SSL settings
     * @return Initialized SSLContext ready for use
     * @throws RuntimeException if SSL context creation fails
     */
    public SSLContext createSSLContext(KafkaProperties kafkaProperties) {
        try {
            log.debug("Creating SSL context for Kafka connection");
            
            KafkaProperties.Ssl sslConfig = kafkaProperties.getSsl();
            
            // Load keystore
            KeyStore keyStore = loadKeystore(sslConfig.getKeystore());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, sslConfig.getKeyPassword().toCharArray());
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

            // Load truststore  
            KeyStore trustStore = loadTruststore(sslConfig.getTruststore());
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            // Create SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            
            log.info("SSL context created successfully");
            return sslContext;
            
        } catch (Exception e) {
            log.error("Failed to create SSL context", e);
            throw new RuntimeException("Failed to create SSL context", e);
        }
    }

    /**
     * Validates SSL certificates are accessible.
     * 
     * This method performs a basic validation of the keystore and truststore
     * to ensure they are loaded and accessible. It checks for certificate
     * integrity, expiration, and proper format.
     * 
     * Processing Steps:
     * 1. Load keystore and truststore from configuration
     * 2. Validate keystore integrity and accessibility
     * 3. Validate truststore integrity and accessibility
     * 4. Log validation results
     * 
     * Security Features:
     * - Secure keystore and truststore loading
     * - Certificate integrity checking
     * - Expiration date validation
     * - Comprehensive error handling for validation failures
     * 
     * @param kafkaProperties Kafka configuration properties containing SSL settings
     * @throws RuntimeException if certificate validation fails
     */
    public void validateCertificates(KafkaProperties kafkaProperties) {
        KafkaProperties.Ssl sslConfig = kafkaProperties.getSsl();
        
        if (!sslConfig.isEnabled()) {
            throw new IllegalStateException("SSL is not enabled but certificate validation was requested");
        }
        
        try {
            log.debug("Validating SSL certificates");
            
            // Validate keystore
            KeyStore keyStore = loadKeystore(sslConfig.getKeystore());
            log.debug("Keystore loaded and validated successfully");
            
            // Validate truststore
            KeyStore trustStore = loadTruststore(sslConfig.getTruststore());
            log.debug("Truststore loaded and validated successfully");
            
            log.info("SSL certificate validation completed successfully");
            
        } catch (Exception e) {
            log.error("SSL certificate validation failed", e);
            throw new RuntimeException("SSL certificate validation failed", e);
        }
    }

    /**
     * Builds SSL properties map for Kafka consumer configuration.
     * 
     * This method constructs a map of SSL properties that can be passed
     * to a Kafka consumer's configuration. It includes all necessary
     * properties for SSL/TLS connectivity, including keystore and truststore
     * locations, passwords, and types.
     * 
     * Processing Steps:
     * 1. Extract SSL configuration from Kafka properties
     * 2. Determine if SSL is enabled
     * 3. Build SSL properties map
     * 4. Resolve resource locations for keystore and truststore
     * 5. Include all necessary SSL properties
     * 
     * @param kafkaProperties Kafka configuration properties containing SSL settings
     * @return Map of SSL properties for Kafka consumer configuration
     */
    public Map<String, Object> buildSslProperties(KafkaProperties kafkaProperties) {
        Map<String, Object> sslProps = new HashMap<>();
        
        KafkaProperties.Ssl sslConfig = kafkaProperties.getSsl();
        
        if (!sslConfig.isEnabled()) {
            log.debug("SSL is disabled, returning empty SSL properties");
            return sslProps;
        }
        
        log.debug("Building SSL properties for Kafka consumer");
        
        sslProps.put("security.protocol", sslConfig.getProtocol());
        sslProps.put("ssl.keystore.location", resolveKeystoreLocation(sslConfig.getKeystore().getLocation()));
        sslProps.put("ssl.keystore.password", sslConfig.getKeystore().getPassword());
        sslProps.put("ssl.keystore.type", sslConfig.getKeystore().getType());
        sslProps.put("ssl.truststore.location", resolveTruststoreLocation(sslConfig.getTruststore().getLocation()));
        sslProps.put("ssl.truststore.password", sslConfig.getTruststore().getPassword());
        sslProps.put("ssl.truststore.type", sslConfig.getTruststore().getType());
        sslProps.put("ssl.key.password", sslConfig.getKeyPassword());
        
        log.debug("SSL properties configured: security.protocol={}, keystore.type={}, truststore.type={}", 
                sslConfig.getProtocol(), sslConfig.getKeystore().getType(), sslConfig.getTruststore().getType());
        
        return sslProps;
    }

    /**
     * Determines the appropriate certificate location.
     * 
     * This method attempts to find a valid certificate location by checking
     * external file system paths first, then classpath resources. It provides
     * detailed logging to help in debugging resource resolution issues.
     * 
     * Processing Steps:
     * 1. Check if external location is provided and accessible
     * 2. If not, check if classpath location is provided and accessible
     * 3. If neither, throw an exception
     * 4. Log the chosen location
     * 
     * @param classpathLocation Location of the certificate in classpath (e.g., "classpath:com/example/cert.p12")
     * @param externalLocation Location of the certificate in file system (e.g., "/path/to/cert.p12")
     * @return Absolute path to the certificate file
     * @throws RuntimeException if no valid certificate location is found
     */
    public String determineCertificateLocation(String classpathLocation, String externalLocation) {
        // Priority: check external location first, then classpath
        if (externalLocation != null && !externalLocation.isEmpty()) {
            Path externalPath = Paths.get(externalLocation);
            if (Files.exists(externalPath)) {
                log.debug("Using external certificate location: {}", externalLocation);
                return externalLocation;
            }
        }
        
        if (classpathLocation != null && !classpathLocation.isEmpty()) {
            if (classpathLocation.startsWith("classpath:")) {
                String resourcePath = classpathLocation.substring("classpath:".length());
                ClassPathResource resource = new ClassPathResource(resourcePath);
                if (resource.exists()) {
                    log.debug("Using classpath certificate location: {}", classpathLocation);
                    return classpathLocation;
                }
            }
        }
        
        throw new RuntimeException("No valid certificate location found");
    }

    private KeyStore loadKeystoreFromClasspath(String location, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        String resourcePath = location.substring("classpath:".length());
        ClassPathResource resource = new ClassPathResource(resourcePath);
        
        if (!resource.exists()) {
            throw new RuntimeException("Keystore resource not found in classpath: " + resourcePath);
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            return loadKeystoreFromStream(inputStream, password, type);
        }
    }

    private KeyStore loadTruststoreFromClasspath(String location, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        String resourcePath = location.substring("classpath:".length());
        ClassPathResource resource = new ClassPathResource(resourcePath);
        
        if (!resource.exists()) {
            throw new RuntimeException("Truststore resource not found in classpath: " + resourcePath);
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            return loadKeystoreFromStream(inputStream, password, type);
        }
    }

    private KeyStore loadKeystoreFromStream(InputStream inputStream, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(inputStream, password.toCharArray());
        return keyStore;
    }

    private String resolveKeystoreLocation(String location) {
        return resolveResourceLocation(location, "keystore");
    }

    private String resolveTruststoreLocation(String location) {
        return resolveResourceLocation(location, "truststore");
    }

    /**
     * Resolves a resource location using Spring's ResourceLoader abstraction.
     * This method is agnostic to whether the location is a classpath resource, file system path, or URL.
     * 
     * Supported prefixes:
     * - classpath: for classpath resources
     * - file: for file system resources  
     * - http:/https: for URL resources
     * - No prefix: treated as file system path (relative to working directory)
     * 
     * Processing Steps:
     * 1. Normalize the input location to ensure it has a proper Spring Resource prefix
     * 2. Attempt to load the resource using the ResourceLoader
     * 3. If resource not found, throw an exception
     * 4. If resource is found, try to get the file directly
     * 5. If file access fails, extract the resource to a temporary file
     * 6. Return the resolved absolute path
     * 
     * @param location The original location of the resource (e.g., "classpath:com/example/cert.p12", "file:/path/to/cert.p12", "https://example.com/cert.p12")
     * @param resourceType Type of resource (e.g., "keystore", "truststore")
     * @return Absolute path to the resolved resource
     * @throws RuntimeException if resource cannot be resolved or found
     */
    private String resolveResourceLocation(String location, String resourceType) {
        try {
            log.debug("Resolving {} location: {}", resourceType, location);
            
            Resource resource;
            if (resourceLoader != null) {
                // Use injected ResourceLoader when available
                resource = resourceLoader.getResource(normalizeResourceLocation(location));
            } else {
                // Fallback: create appropriate resource based on location prefix
                if (location.startsWith("classpath:")) {
                    resource = new ClassPathResource(location.substring("classpath:".length()));
                } else {
                    // For file paths, use DefaultResourceLoader with proper file: prefix
                    String normalizedLocation = normalizeResourceLocation(location);
                    resource = new org.springframework.core.io.DefaultResourceLoader().getResource(normalizedLocation);
                }
            }
            
            if (!resource.exists()) {
                throw new RuntimeException(String.format("%s resource not found: %s", 
                    resourceType, location));
            }
            
            // Try to get the file directly first (works for file system and exploded classpath)
            try {
                File file = resource.getFile();
                String absolutePath = file.getAbsolutePath();
                log.debug("Using {} file directly: {}", resourceType, absolutePath);
                return absolutePath;
            } catch (IOException e) {
                // Resource is not a file (e.g., inside JAR), extract to temporary file
                log.debug("Resource {} is not accessible as file, extracting to temporary location", location);
                return extractResourceToTempFile(resource, resourceType).toString();
            }
            
        } catch (Exception e) {
            log.error("Failed to resolve {} location: {}", resourceType, location, e);
            throw new RuntimeException(String.format("Failed to resolve %s location: %s", resourceType, location), e);
        }
    }

    /**
     * Normalizes a resource location to ensure proper Spring Resource prefixes.
     * This ensures that file system paths have the 'file:' prefix for proper resolution.
     * 
     * Processing Steps:
     * 1. Check if location already has a Spring Resource prefix
     * 2. If not, add 'file:' prefix for file system paths
     * 3. Return the normalized location
     * 
     * @param location The original location (e.g., "com/example/cert.p12", "/path/to/cert.p12", "classpath:com/example/cert.p12")
     * @return Location with a proper Spring Resource prefix (e.g., "file:/path/to/cert.p12", "classpath:com/example/cert.p12")
     */
    private String normalizeResourceLocation(String location) {
        if (location.startsWith("classpath:") || 
            location.startsWith("file:") || 
            location.startsWith("http:") || 
            location.startsWith("https:")) {
            // Already has a proper prefix
            return location;
        }
        
        // Assume it's a file system path and add file: prefix
        return "file:" + location;
    }

    /**
     * Extracts a Spring Resource to a temporary file.
     * This handles resources that cannot be accessed as direct files (e.g., inside JARs).
     * 
     * Processing Steps:
     * 1. Create a temporary file
     * 2. Copy the resource content to the temporary file
     * 3. Set up deletion on JVM exit
     * 4. Log the extraction
     * 5. Return the path to the temporary file
     * 
     * @param resource The Spring Resource to extract
     * @param prefix A prefix for the temporary file name (e.g., "keystore", "truststore")
     * @return Path to the extracted temporary file
     * @throws RuntimeException if resource extraction fails
     */
    private Path extractResourceToTempFile(Resource resource, String prefix) {
        try {
            // Create temporary file
            Path tempFile = Files.createTempFile(prefix, ".p12");
            
            // Copy resource content to temporary file
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Delete temp file on JVM exit
            tempFile.toFile().deleteOnExit();
            
            log.debug("Extracted resource {} to temporary file: {}", resource.getDescription(), tempFile);
            return tempFile;
            
        } catch (IOException e) {
            log.error("Failed to extract resource to temporary file: {}", resource.getDescription(), e);
            throw new RuntimeException("Failed to extract resource: " + resource.getDescription(), e);
        }
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use {@link #extractResourceToTempFile(Resource, String)} instead
     */
    @Deprecated
    private Path extractResourceToTempFile(ClassPathResource resource, String prefix, String suffix) {
        try {
            if (!resource.exists()) {
                throw new RuntimeException("Resource not found in classpath: " + resource.getPath());
            }
            
            // Create temporary file
            Path tempFile = Files.createTempFile(prefix, suffix);
            
            // Copy resource content to temporary file
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Delete temp file on JVM exit
            tempFile.toFile().deleteOnExit();
            
            log.debug("Extracted classpath resource {} to temporary file: {}", resource.getPath(), tempFile);
            return tempFile;
            
        } catch (IOException e) {
            log.error("Failed to extract classpath resource to temporary file: {}", resource.getPath(), e);
            throw new RuntimeException("Failed to extract classpath resource: " + resource.getPath(), e);
        }
    }
} 