package net.minecraftforge.gradle.common.util.runs;

import com.google.common.collect.Maps;
import net.minecraftforge.gradle.common.util.RunConfig;
import net.minecraftforge.gradle.common.util.Utils;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IntellijRunGenerator extends RunConfigGenerator.XMLConfigurationBuilder
{
    @Override
    @Nonnull
    protected Map<String, Document> createRunConfiguration(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props, @Nonnull final DocumentBuilder documentBuilder) {
        final Map<String, Document> documents = new LinkedHashMap<>();

        // Java run config
        final Document javaDocument = documentBuilder.newDocument();
        {
            final Element rootElement = javaDocument.createElement("component");
            {
                final Element configuration = javaDocument.createElement("configuration");
                {
                    configuration.setAttribute("default", "false");
                    configuration.setAttribute("name", runConfig.getUniqueName());
                    configuration.setAttribute("type", "Application");
                    configuration.setAttribute("factoryName", "Application");
                    configuration.setAttribute("singleton", runConfig.isSingleInstance() ? "true" : "false");

                    elementOption(javaDocument, configuration, "MAIN_CLASS_NAME", runConfig.getMain());
                    elementOption(javaDocument, configuration, "VM_PARAMETERS", props);
                    elementOption(javaDocument, configuration, "PROGRAM_PARAMETERS", String.join(" ", runConfig.getArgs()));
                    elementOption(javaDocument, configuration, "WORKING_DIRECTORY", replaceRootDirBy(project, runConfig.getWorkingDirectory(), "$PROJECT_DIR$"));

                    final Element module = javaDocument.createElement("module");
                    {
                        module.setAttribute("name", runConfig.getIdeaModule());
                    }
                    configuration.appendChild(module);

                    final Element envs = javaDocument.createElement("envs");
                    {
                        Map<String, String> environment = Maps.newHashMap(runConfig.getEnvironment());
                        environment.computeIfAbsent("MOD_CLASSES", (key) -> replaceRootDirBy(project, mapModClassesToIdea(project, runConfig), "$PROJECT_DIR$"));
                        environment.forEach((name, value) -> {
                            final Element envEntry = javaDocument.createElement("env");
                            {
                                envEntry.setAttribute("name", name);
                                envEntry.setAttribute("value", value);
                            }
                            envs.appendChild(envEntry);
                        });
                    }
                    configuration.appendChild(envs);

                    final Element methods = javaDocument.createElement("method");
                    {
                        methods.setAttribute("v", "2");

                        final Element makeTask = javaDocument.createElement("option");
                        {
                            makeTask.setAttribute("name", "Make");
                            makeTask.setAttribute("enabled", "true");
                        }
                        methods.appendChild(makeTask);

                        final Element gradleTask = javaDocument.createElement("option");
                        {
                            gradleTask.setAttribute("name", "Gradle.BeforeRunTask");
                            gradleTask.setAttribute("enabled", "true");
                            gradleTask.setAttribute("tasks", project.getTasks().getByName("prepare" + Utils.capitalize(runConfig.getTaskName())).getPath());
                            gradleTask.setAttribute("externalProjectPath", "$PROJECT_DIR$");
                        }
                        methods.appendChild(gradleTask);
                    }
                    configuration.appendChild(methods);
                }
                rootElement.appendChild(configuration);
            }
            javaDocument.appendChild(rootElement);
        }
        documents.put(runConfig.getUniqueFileName() + ".xml", javaDocument);

        return documents;
    }

    private static String mapModClassesToIdea(@Nonnull final Project project, @Nonnull final RunConfig runConfig) {
        final IdeaModel idea = project.getExtensions().findByType(IdeaModel.class);

        JavaPluginConvention javaPlugin = project.getConvention().getPlugin(JavaPluginConvention.class);
        SourceSetContainer sourceSets = javaPlugin.getSourceSets();
        final SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        if (runConfig.getMods().isEmpty()) {
            return getIdeaPathsForSourceset(project, idea, "production", null);
        } else {

            return runConfig.getMods().stream()
                    .map(modConfig -> {
                        return modConfig.getSources().stream().map(source -> {
                            String outName = source == main ? "production" : source.getName();
                            return getIdeaPathsForSourceset(project, idea, outName, modConfig.getName());
                        });
                    })
                    .flatMap(Function.identity())
                    .collect(Collectors.joining(File.pathSeparator));
        }
    }

    private static String getIdeaPathsForSourceset(@Nonnull Project project, @Nullable IdeaModel idea, String outName, @Nullable String modName)
    {
        String ideaResources, ideaClasses;
        try
        {
            String outputPath = idea != null
                    ? idea.getProject().getPathFactory().path("$PROJECT_DIR$").getCanonicalUrl()
                    : project.getProjectDir().getCanonicalPath();

            ideaResources = Paths.get(outputPath, "out", outName, "resources").toFile().getCanonicalPath();
            ideaClasses = Paths.get(outputPath, "out", outName, "classes").toFile().getCanonicalPath();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error getting paths for idea run configs", e);
        }

        if (modName != null)
        {
            ideaResources = modName + "%%" + ideaResources;
            ideaClasses = modName + "%%" + ideaClasses;
        }

        return String.join(File.pathSeparator, ideaResources, ideaClasses);
    }
}
