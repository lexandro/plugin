package com.lexandro.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.*;
import java.net.URI;

/**
 * Genereates a PoC source code and adds to the build.
 */
@Mojo(
        name = "codegen",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresProject = true

)
public class CodegenDemo extends AbstractMojo {
    // TODO add input/output encoding, includes/excludes, source/destination dir,

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/codegen")
    private File outputDirectory;

    /**
     * The current Maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;


    @Component
    private BuildContext buildContext;

    public File getOutputDirectory() {
        return outputDirectory;
    }

    void addSourceRoot(File outputDir) {
        project.addCompileSourceRoot(outputDir.getPath());
    }

    public void execute() throws MojoExecutionException {
        Log log = getLog();
        log.info("Generating source(s)");

        File outputDir = getOutputDirectory();

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        log.debug("Output directory base will be " + outputDirectory.getAbsolutePath());

        File outputFile = new File(outputDirectory, "hello.java");
        URI relativePath = project.getBasedir().toURI().relativize(outputFile.toURI());
        log.debug("  Writing file: " + relativePath);

        OutputStream outputStream = null;
        try {
            outputStream = buildContext.newFileOutputStream(outputFile);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write("Hello!");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
