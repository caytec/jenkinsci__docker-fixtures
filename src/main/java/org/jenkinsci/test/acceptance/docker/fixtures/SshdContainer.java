package org.jenkinsci.test.acceptance.docker.fixtures;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.jenkinsci.test.acceptance.docker.DockerFixture;
import org.jenkinsci.utils.process.CommandBuilder;
import org.jenkinsci.utils.process.ProcessInputStream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumSet;

import static java.nio.file.attribute.PosixFilePermission.*;
import org.apache.commons.io.IOUtils;

/**
 * Represents a server with SSHD.
 *
 * @author Kohsuke Kawaguchi
 */
@DockerFixture(id = "sshd", ports = 22)
public class SshdContainer extends DockerContainer {
    private File privateKey;
    private File privateKeyEnc;

    /**
     * Get plaintext Private Key File
     */
    public File getPrivateKey() {
        if (privateKey == null) {
            try {
                privateKey = Files.createTempFile("ssh", "key").toFile();
                privateKey.deleteOnExit();
                FileUtils.copyURLToFile(resource("unsafe").url, privateKey);
                if (SystemUtils.IS_OS_UNIX) {
                    Files.setPosixFilePermissions(privateKey.toPath(), EnumSet.of(OWNER_READ));
                }
            } catch (IOException e) {
                throw new RuntimeException("Not able to get the plaintext SSH key file. Missing file, wrong file permissions?!");
            }
        }
        return privateKey;
    }

    /**
     * Get encrypted Private Key File
     */
    public File getEncryptedPrivateKey() {
        if (privateKeyEnc == null) {
            try {
                privateKeyEnc = Files.createTempFile("ssh_enc", "key").toFile();
                privateKeyEnc.deleteOnExit();
                FileUtils.copyURLToFile(resource("unsafe_enc_key").url, privateKeyEnc);
                if (SystemUtils.IS_OS_UNIX) {
                    Files.setPosixFilePermissions(privateKeyEnc.toPath(), EnumSet.of(OWNER_READ));
                }
            } catch (IOException e) {
                throw new RuntimeException("Not able to get the encrypted SSH key file. Missing file, wrong file permissions?!");
            }
        }
        return privateKeyEnc;
    }

    @SuppressFBWarnings(value = "DM_DEFAULT_ENCODING", justification = "TODO needs triage")
    public String getPrivateKeyString() {
        try {
            return new String(Files.readAllBytes(getPrivateKey().toPath()));
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * Gets the SSH command line via <b>unencrypted</b> key.
     */
    public CommandBuilder ssh() {
        return new CommandBuilder("ssh")
                .add("-p", port(22), "-F", "none", "-o", "IdentitiesOnly=yes", "-o", "StrictHostKeyChecking=no", "-i", getPrivateKey(), "test@" + ipBound(22));
        // TODO as in CLITest.tempHome this would better use a custom home directory so any existing ~/.ssh/known_hosts is ignored
    }


//    /**
//     * Gets the SSH command line via <b>encrypted</b> key.
//     * FIXME additonal script or tool needed like sshpass ("sshpass -fpassword.txt ssh -p 22 -o ... -i ... test@localhost")
//     * ssh does not allow passing a password as a parameter!
//     */
//    public CommandBuilder ssh_enc() throws IOException, InterruptedException {
//        return new CommandBuilder("ssh")
//                .add("-p",port(22),"-o","StrictHostKeyChecking=no","-i",getPrivateKey(),"test@localhost");
//    }

    /**
     * Login with SSH public key and run some command.
     */
    public void sshWithPublicKey(CommandBuilder cmd) throws IOException, InterruptedException {
        ProcessInputStream pis = ssh().add(cmd).popen();
        // Avoid CommandBuilder.system() as it uses ProcessBuilder.Redirect.INHERIT which confuses Surefire.
        IOUtils.copy(pis, System.err);
        if (pis.waitFor() != 0) {
            throw new AssertionError("ssh failed: " + cmd);
        }
    }

    public ProcessInputStream popen(CommandBuilder cmd) throws IOException, InterruptedException {
        return ssh().add(cmd).popen();
    }
}
