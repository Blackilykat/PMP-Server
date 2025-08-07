/*
 * Copyright (C) 2025 Blackilykat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.blackilykat;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.mindrot.jbcrypt.BCrypt;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class Main {
    public static final Logger LOGGER = LogManager.getLogger(Main.class);
    public static final int PASSWORD_LOG_ROUNDS = 15;
    public static ArrayList<Client> clients = new ArrayList<>();
    public static int clientIdCounter = 0;

    public static void main(String[] args) throws IOException {
        LOGGER.info("Starting...");

        System.setOut(loggingProxy(System.out, Level.INFO));
        System.setErr(loggingProxy(System.err, Level.ERROR));

        boolean passwordArg = Arrays.asList(args).contains("--password");

        LOGGER.info("Initializing storage...");
        Storage.init(!passwordArg);
        LOGGER.info("Initialized storage");

        if(!Storage.general.containsKey("password") || passwordArg) {
            Console console = System.console();
            if(console == null) {
                // If you run the program in your IDE's terminal and it exits here, try running it in a real terminal.
                LOGGER.fatal("Need a terminal to read password. Exiting");
                System.exit(1);
            }

            String hashedPassword = null;
            {
                try {
                    char[] password;
                    while(true) {
                        System.out.print("Insert password: ");
                        password = console.readPassword();
                        System.out.print("Insert password again: ");
                        if(!Arrays.equals(console.readPassword(), password)) {
                            LOGGER.info("Passwords don't match!");
                            continue;
                        }
                        break;
                    }

                    hashedPassword = BCrypt.hashpw(new String(password), BCrypt.gensalt(PASSWORD_LOG_ROUNDS));
                } catch(IOError e) {
                    // unreachable i think
                    throw new RuntimeException(e);
                }
            }
            Storage.general.put("password", hashedPassword);
            LOGGER.info("Password set");

            if(passwordArg) {
                Storage.devices.forEach((integer, device) -> {
                    device.token = null;
                });
                LOGGER.info("All tokens have been invalidated. You will need to insert the new password on each device.");
                LOGGER.info("Found password argument, exiting");
                System.exit(0);
            }
        }

        LOGGER.info("Preparing SSL...");
        SSLContext sslContext;
        try {
            Security.addProvider(new BouncyCastleProvider());
            char[] keyPassword = "key".toCharArray();
            File keyStoreFile = new File("keystore.jks");
            KeyStore keyStore = KeyStore.getInstance("BCFKS", "BC");
            if(!keyStoreFile.exists()) {
                // https://github.com/rodbate/bouncycastle-examples/blob/master/src/main/java/bcfipsin100/tls/Simple.java
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(2048);
                KeyPair keyPair = keyPairGenerator.generateKeyPair();

                X509v1CertificateBuilder builder = new JcaX509v1CertificateBuilder(
                        new X500Name("CN=PMP Server"),
                        BigInteger.valueOf(System.currentTimeMillis()),
                        new Date(System.currentTimeMillis() - 5000L),
                        new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 2000), // expires in 2000 years (basically never)
                        new X500Name("CN=PMP Server"),
                        keyPair.getPublic()
                );
                JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder("SHA384withRSA").setProvider("BC");
                X509Certificate certificate = new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signerBuilder.build(keyPair.getPrivate())));

                keyStore.load(null, null);
                keyStore.setKeyEntry("Key", keyPair.getPrivate(), keyPassword, new X509Certificate[]{certificate});
                keyStore.store(new FileOutputStream(keyStoreFile), null);
            } else {
                keyStore.load(new FileInputStream(keyStoreFile), null);
            }

            KeyManagerFactory factory = KeyManagerFactory.getInstance("SunX509");
            factory.init(keyStore, keyPassword);

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(factory.getKeyManagers(), null, SecureRandom.getInstance("DEFAULT", "BC"));
        } catch(OperatorCreationException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Prepared SSL");

        LOGGER.info("Starting file transfer server...");
        HttpsServer fileTransferHttpServer = HttpsServer.create(new InetSocketAddress(5001), 0);
        fileTransferHttpServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        fileTransferHttpServer.createContext("/", new FileTransferHttpHandler());
        fileTransferHttpServer.start();
        LOGGER.info("Started file transfer server");

        LOGGER.info("Starting main server");

        SSLServerSocket serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(5000);

        while(true) {
            try {
                Client client = new Client((SSLSocket) serverSocket.accept(), clientIdCounter++);
                LOGGER.info("Connected to client {}", client);

                client.startSending();
                client.startReceiving();
            } catch(Exception e) {
                LOGGER.warn("Failed to connect a client.", e);
            } catch(Throwable e) {
                // Errors are designed to not be caught and shut down the program. They should print their stacktraces anyway
                // but you can never be too safe
                LOGGER.fatal(e);
                throw e;
            }
        }
    }

    public static PrintStream loggingProxy(PrintStream stream, Level level) {
        String tPrefix = "(stream)";
        if(stream == System.out) tPrefix = "(stdout)";
        else if(stream == System.err) tPrefix = "(stderr)";

        final String prefix = tPrefix;

        return new PrintStream(stream) {
            @Override
            public void println() {
            }

            @Override
            public void println(int x) {
                print(x);
            }

            @Override
            public void println(char x) {
                print(x);
            }

            @Override
            public void println(long x) {
                print(x);
            }

            @Override
            public void println(float x) {
                print(x);
            }

            @Override
            public void println(char[] x) {
                print(x);
            }

            @Override
            public void println(double x) {
                print(x);
            }

            @Override
            public void println(Object x) {
                print(x);
            }

            @Override
            public void println(String x) {
                print(x);
            }

            @Override
            public void println(boolean x) {
                print(x);
            }

            @Override
            public void print(String s) {
                LOGGER.log(level, "{} {}", prefix, s);
            }

            @Override
            public void print(Object obj) {
                if(obj instanceof Exception ex) {
                    LOGGER.log(level, "{} exception", prefix, ex);
                    return;
                } else if(obj instanceof String str){
                    if(str.startsWith("\tat ")) return;
                }

                LOGGER.log(level, "{} {}", prefix, obj);
            }

            @Override
            public void print(int i) {
                LOGGER.log(level, "{} {}", prefix, i);
            }

            @Override
            public void print(char c) {
                LOGGER.log(level, "{} {}", prefix, c);
            }

            @Override
            public void print(long l) {
                LOGGER.log(level, "{} {}", prefix, l);
            }

            @Override
            public void print(boolean b) {
                LOGGER.log(level, "{} {}", prefix, b);
            }

            @Override
            public void print(float f) {
                LOGGER.log(level, "{} {}", prefix, f);
            }

            @Override
            public void print(double d) {
                LOGGER.log(level, "{} {}", prefix, d);
            }

            @Override
            public void print(char[] s) {
                LOGGER.log(level, "{} {}", prefix, s);
            }
        };
    }
}
