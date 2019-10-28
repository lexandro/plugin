package com.lexandro.plugin;

import java.io.File;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.transfer.dependencies.DependableCoordinate;

/**
 * ArtifactItem represents information specified in the plugin configuration section for each artifact.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class ArtifactItem implements DependableCoordinate {

    /**
     * Group Id of Artifact
     *
     * @parameter
     * @required
     */
    private String groupId;

    /**
     * Name of Artifact
     *
     * @parameter
     * @required
     */
    private String artifactId;

    /**
     * Version of Artifact
     *
     * @parameter
     */
    private String version = null;

    /**
     * Type of Artifact (War,Jar,etc)
     *
     * @parameter
     * @required
     */
    private String type = "jar";

    /**
     * Classifier for Artifact (tests,sources,etc)
     *
     * @parameter
     */
    private String classifier;

    /**
     * Location to use for this Artifact. Overrides default location.
     *
     * @parameter
     */
    private File outputDirectory;

    /**
     * Force Overwrite..this is the one to set in pom
     */
    private String overWrite;

    private Artifact artifact;

    public ArtifactItem(Artifact artifact) {
        this.setArtifact(artifact);
        this.setArtifactId(artifact.getArtifactId());
        this.setClassifier(artifact.getClassifier());
        this.setGroupId(artifact.getGroupId());
        this.setType(artifact.getType());
        this.setVersion(artifact.getVersion());
    }
}
