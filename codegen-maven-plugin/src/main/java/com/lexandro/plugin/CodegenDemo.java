package com.lexandro.plugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.List;
import lombok.Getter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.sonatype.plexus.build.incremental.BuildContext;

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
    @Getter
    private File outputDirectory;

    /**
     * The current Maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    /**
     * The Maven session
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;
    /**
     * Remote repositories which will be searched for artifacts.
     */
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> remoteRepositories;

    /**
     * Collection of ArtifactItems to work on. (ArtifactItem contains groupId, artifactId, version, type, classifier, outputDirectory, destFileName, overWrite and encoding.) See <a
     * href="./usage.html">Usage</a> for details.
     *
     * @since 1.0
     */
    @Parameter
    private List<ArtifactItem> artifactItems;

    @Component
    private BuildContext buildContext;

    @Component
    private ArchiverManager archiverManager;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    @Component
    private ArtifactResolver artifactResolver;

    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        log.info("Generating source(s)");

        if (artifactItems == null || artifactItems.isEmpty()) {
            throw new MojoFailureException("Either artifact or artifactItems is required ");
        } else {
            log.info("Processing artifact(s)");
            for (ArtifactItem artifactItem : artifactItems) {
                unpackArtifact(artifactItem);
            }
        }

        File outputDir = getOutputDirectory();

        if (!outputDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outputDir.mkdirs();
        }
        log.info("Output directory base will be " + outputDirectory.getAbsolutePath());

        File outputFile = new File(outputDirectory, "Hello.java");
        URI relativePath = project.getBasedir().toURI().relativize(outputFile.toURI());
        log.info("  Writing file: " + relativePath);

        OutputStream outputStream = null;
        try {
            outputStream = buildContext.newFileOutputStream(outputFile);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write("package com.lexandro.plugin.demo;\n\n"
                + "public class Hello {\n"
                + "\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello from generated class\");\n"
                + "    }\n"
                + "}");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        log.info("Code generation done");
        if (project != null) {
            for (ArtifactItem artifactItem : artifactItems) {
                unpackArtifact(artifactItem);
                addSourceRoot(artifactItem.getOutputDirectory());
            }
            addSourceRoot(this.getOutputDirectory());
        }

    }

    private void unpackArtifact(ArtifactItem artifactItem) throws MojoExecutionException {
        File file = getArtifact(artifactItem).getFile();
        File location = null;
        try {

            location = artifactItem.getOutputDirectory();
            location.mkdirs();
            if (!location.exists()) {
                throw new MojoExecutionException("Location to write unpacked files to could not be created: "
                    + location);
            }

            if (file.isDirectory()) {
                // usual case is a future jar packaging, but there are special cases: classifier and other packaging
                throw new MojoExecutionException("Artifact has not been packaged yet. When used on reactor artifact, "
                    + "unpack should be executed after packaging: see MDEP-98.");
            }

            UnArchiver unArchiver;
            String type = artifactItem.getType();
            try {
                unArchiver = archiverManager.getUnArchiver(type);
                getLog().info("Found unArchiver by type: " + unArchiver);
            } catch (NoSuchArchiverException e) {
                unArchiver = archiverManager.getUnArchiver(file);
                getLog().info("Found unArchiver by extension: " + unArchiver);
            }
//
//            if (encoding != null && unArchiver instanceof ZipUnArchiver) {
//                ((ZipUnArchiver) unArchiver).setEncoding(encoding);
//                getLog().info("Unpacks '" + type + "' with encoding '" + encoding + "'.");
//            }
//
//            unArchiver.setIgnorePermissions(ignorePermissions);

            unArchiver.setSourceFile(file);

            unArchiver.setDestDirectory(location);

            unArchiver.extract();
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("Unknown archiver type", e);
        } catch (ArchiverException e) {
            throw new MojoExecutionException("Error unpacking file: " + file + " to: " + location
                + System.lineSeparator() + e.toString(), e);
        }
    }

    protected Artifact getArtifact(ArtifactItem artifactItem) throws MojoExecutionException {
        Artifact artifact;

        try {
            ProjectBuildingRequest buildingRequest = newResolveArtifactProjectBuildingRequest();

            // Map dependency to artifact coordinate
            DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
            coordinate.setGroupId(artifactItem.getGroupId());
            coordinate.setArtifactId(artifactItem.getArtifactId());
            coordinate.setVersion(artifactItem.getVersion());
            coordinate.setClassifier(artifactItem.getClassifier());

            final String extension;
            ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(artifactItem.getType());
            if (artifactHandler != null) {
                extension = artifactHandler.getExtension();
            } else {
                extension = artifactItem.getType();
            }
            coordinate.setExtension(extension);

            artifact = artifactResolver.resolveArtifact(buildingRequest, coordinate).getArtifact();
        } catch (ArtifactResolverException e) {
            throw new MojoExecutionException("Unable to find/resolve artifact.", e);
        }

        return artifact;
    }

    public ProjectBuildingRequest newResolveArtifactProjectBuildingRequest() {
        ProjectBuildingRequest buildingRequest =
            new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        buildingRequest.setRemoteRepositories(remoteRepositories);

        return buildingRequest;
    }

    void addSourceRoot(File outputDir) {
        project.addCompileSourceRoot(outputDir.getPath());
    }
}
