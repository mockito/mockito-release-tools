package org.shipkit.internal.gradle.java.tasks;

import org.gradle.api.artifacts.*;
import org.json.simple.JsonArray;
import org.json.simple.JsonObject;
import org.json.simple.Jsoner;
import org.shipkit.internal.notes.util.IOUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class CreateDependencyInfoFile {

    private static final String DESCRIPTION = "This file was generated by Shipkit Gradle plugin. See http://shipkit.org.";

    public void createDependencyInfoFile(CreateDependencyInfoFileTask task) {
        JsonObject result = createJsonObject();
        result.put("description", DESCRIPTION);

        JsonArray dependencies = createJsonArray();

        for (Dependency dependency: task.getConfiguration().getAllDependencies()) {
            if (dependency instanceof ModuleDependency) {
                addResolvedDependency(dependencies, (ModuleDependency) dependency);
            }
        }

        result.put("dependencies", dependencies);

        IOUtil.writeFile(task.getOutputFile(), Jsoner.prettyPrint(result.toJson()));
    }

    private void addResolvedDependency(JsonArray dependencies, ModuleDependency dependency) {
        if (!dependency.getArtifacts().isEmpty()) {
            for (DependencyArtifact artifact : dependency.getArtifacts()) {
                dependencies.add(getDependencyForSingleArtifact(dependency, artifact));
            }
        } else {
            dependencies.add(getDependencyForSingleArtifact(dependency, null));
        }
    }

    private JsonObject getDependencyForSingleArtifact(ModuleDependency dependency, DependencyArtifact artifact) {
        JsonObject result = createJsonObject();

        result.put("group", dependency.getGroup());
        result.put("name", dependency.getName());
        result.put("version", dependency.getVersion());
        result.put("artifact", getArtifact(artifact));

        return result;
    }

    private JsonObject getArtifact(DependencyArtifact artifact) {
        JsonObject result = createJsonObject();

        result.put("name", artifact == null ? null : artifact.getName());
        result.put("classifier", artifact == null ? null : artifact.getClassifier());
        result.put("extension", artifact == null ? null : artifact.getExtension());
        result.put("type", artifact == null ? null : artifact.getType());

        return result;
    }

    private JsonObject createJsonObject() {
        return new JsonObject(new LinkedHashMap<String, Object>()); // linkedHashMap to keep the order
    }

    private JsonArray createJsonArray() {
        return new JsonArray(new ArrayList<Object>()); //arrayList to keep the order
    }


}
