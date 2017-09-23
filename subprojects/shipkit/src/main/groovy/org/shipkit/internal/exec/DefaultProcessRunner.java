package org.shipkit.internal.exec;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.shipkit.internal.notes.util.IOUtil;
import org.shipkit.internal.notes.util.ReleaseNotesException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.shipkit.internal.gradle.util.StringUtil.join;

public class DefaultProcessRunner implements ProcessRunner {

    private static final Logger LOG = Logging.getLogger(DefaultProcessRunner.class);
    private final File workDir;
    private final File outputLogFile;
    private List<String> secretValues = Collections.emptyList();

    /**
     * Calls {@link #DefaultProcessRunner(File, File)}
     */
    public DefaultProcessRunner(File workDir) {
        this(workDir, null);
    }

    /**
     * Create Process runner
     * @param workDir Work directory where to start a process
     * @param outputLogFile If process create a long output it's better to save it in file
     */
    public DefaultProcessRunner(File workDir, File outputLogFile) {
        this.workDir = workDir;
        this.outputLogFile = outputLogFile;
    }

    public String run(String... commandLine) {
        return run(asList(commandLine));
    }

    public String run(List<String> commandLine) {
        return run(LOG, commandLine);
    }

    String run(Logger log, List<String> commandLine) {
        // WARNING!!! ensure that masked command line is used for all logging!!!
        String maskedCommandLine = mask(join(commandLine, " "));
        log.lifecycle("  Executing:\n    " + maskedCommandLine);

        ProcessResult result = executeProcess(commandLine, maskedCommandLine);

        if (result.getExitValue() != 0) {
            return executionOfCommandFailed(maskedCommandLine, result);
        } else {
            String output = result.getOutput();
            LOG.info("Output from external process '{}':\n{}", maskedCommandLine, output);
            return output;
        }
    }

    private ProcessResult executeProcess(List<String> commandLine, String maskedCommandLine) {
        ProcessResult result;
        try {
            Process process = new ProcessBuilder(commandLine).directory(workDir).redirectErrorStream(true).start();
            String output = mask(readFully(new BufferedReader(new InputStreamReader(process.getInputStream()))));
            storeOutputToFile(output);

            //TODO add sanity timeout when we move to Java 1.7
            // 1. we can do something like process.waitFor(15, TimeUnit.MINUTES)
            // 2. first, we need to change the compatibility, push to Gradle 3.0, stop building with Java 1.6.
            process.waitFor();

            result = new ProcessResult(output, process);
        } catch (Exception e) {
            throw new ReleaseNotesException("Problems executing command:\n  " + maskedCommandLine, e);
        }
        return result;
    }

    private String mask(String text) {
        if (secretValues.isEmpty()) {
            return text;
        }
        for (String s : secretValues) {
            if (s != null) {
                text = text.replace(s, "[SECRET]");
            }
        }
        return text;
    }

    private static String readFully(BufferedReader reader) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private void storeOutputToFile(String content) {
        if (outputLogFile != null) {
            //TODO ms - can we make sure that the output does not have sensitive secret values
            //should we mask secret values in the output stored in file, too?
            //TODO SF - good question. Currently secret values are masked (see invocation of this method)
            //On Travis CI you can't see files in workspace, but maybe it would be good to publish this outputs somewhere
            //for example gist.github.com (?), then we should mask secrets
            IOUtil.writeFile(outputLogFile, content);
        }
    }

    private String executionOfCommandFailed(String maskedCommandLine, ProcessResult result) {
        String message = "Execution of command failed (exit code " + result.getExitValue() + "):\n" +
                "  " + maskedCommandLine + "\n";
        if (outputLogFile == null) {
            message = message + "  Captured command output:\n" + result.getOutput();
        } else {
            message = message + "  Captured command output stored in " + outputLogFile;
        }
        throw new GradleException(message);
    }

    /**
     * @param secretValue to be masked from the output and logging
     * @return this runner
     */
    public DefaultProcessRunner setSecretValue(String secretValue) {
        return setSecretValues(asList(secretValue));
    }

    /**
     * @param secretValues all values will be masked from the output and logging
     */
    public DefaultProcessRunner setSecretValues(List<String> secretValues) {
        this.secretValues = secretValues;
        return this;
    }

    private static class ProcessResult {
        private final String output;
        private final Process process;

        public ProcessResult(String output, Process process) {
            this.output = output;
            this.process = process;
        }

        public String getOutput() {
            return output;
        }

        public int getExitValue() {
            return process.exitValue();
        }
    }
}
