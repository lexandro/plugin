package com.lexandro.plugin;

import lombok.Getter;
import lombok.SneakyThrows;
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
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Genereates a PoC source code and adds to the build.
 */
@Mojo(
        name = "codegen",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public class CodegenDemo extends AbstractMojo {

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
                processArtifact(artifactItem);
            }
        }
        log.info("Code generation done");
        if (project != null) {
            project.addCompileSourceRoot(outputDirectory.getPath());
        }
    }

    @SneakyThrows
    private void processArtifact(ArtifactItem artifactItem) {
        File outputDir = getOutputDirectory();

        if (!outputDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outputDir.mkdirs();
        }
        getLog().info("Output directory base will be " + outputDirectory.getAbsolutePath());

        Files.walk(Paths.get(artifactItem.getOutputDirectory().getAbsolutePath()))
                .filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(".yml") || f.toString().endsWith(".yaml"))
                .forEach(this::processApiDefinition);
    }

    @SneakyThrows
    private void processApiDefinition(Path filePath) {
        InputStream inputStream = new FileInputStream(filePath.toAbsolutePath().toString());

        Yaml yaml = new Yaml(new Constructor(Api.class));
        Api api = yaml.load(inputStream);

        File outputFile = new File(outputDirectory, api.getName() + ".java");
        URI relativePath = project.getBasedir().toURI().relativize(outputFile.toURI());
        getLog().info("  Writing file: " + relativePath);

        OutputStream outputStream;
        outputStream = buildContext.newFileOutputStream(outputFile);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
        writer.write(generateJava(api));
        writer.flush();
        writer.close();
    }

    private String generateJava(Api api) {
        String fields = api.getFields().stream().map(f -> String.format("\n\t//Generated code: \n\tprivate %s %s;", f.getType(), f.getName())).collect(Collectors.joining("\n"));
        return String.format("package com.lexandro.plugin.demo;\n\n"
                + "public class %s {\n"
                + "%s"
                + "\n\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello from generated class: %s\");\n"
                + "    }\n"
                + "}", api.getName(), fields, api.getDescription());
    }

    private void unpackArtifact(ArtifactItem artifactItem) throws MojoExecutionException {
        File file = getArtifact(artifactItem).getFile();
        File location = null;
        try {

            location = artifactItem.getOutputDirectory();
            //noinspection ResultOfMethodCallIgnored
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
                getLog().debug("Found unArchiver by type: " + unArchiver);
            } catch (NoSuchArchiverException e) {
                unArchiver = archiverManager.getUnArchiver(file);
                getLog().debug("Found unArchiver by extension: " + unArchiver);
            }

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

    @SneakyThrows
    private Artifact getArtifact(ArtifactItem artifactItem) {
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

        return artifactResolver.resolveArtifact(buildingRequest, coordinate).getArtifact();
    }

    private ProjectBuildingRequest newResolveArtifactProjectBuildingRequest() {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        buildingRequest.setRemoteRepositories(remoteRepositories);

        return buildingRequest;
    }

}
