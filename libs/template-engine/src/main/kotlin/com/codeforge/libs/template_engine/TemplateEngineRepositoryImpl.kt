package com.codeforge.libs.template_engine

import android.content.Context
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.ProjectTemplateContribution
import com.codeforge.core.domain.extension.Resolved
import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.ProjectTemplateKind
import com.codeforge.core.domain.model.PluginTemplateRef
import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import com.codeforge.core.domain.repository.TemplateEngineRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erzeugt Projekte aus Vorlagen (Wizard) auf einem IO-Dispatcher und initialisiert optional ein Git-Repository.
 *
 * Bei Fehlern wird das teilweise erzeugte Projekt wieder gelöscht; Git-Fehler verwerfen das Projekt nicht, sondern werden als Hinweis gemeldet.
 */
@Singleton
class TemplateEngineRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val git: GitRepository,
    private val projectsDirectoryProvider: ProjectsDirectoryProvider,
    private val contributions: ContributionRepository,
) : TemplateEngineRepository {

    private val generator by lazy { ProjectGenerator(AndroidAssetSource(context.assets)) }
    private val pluginGenerator by lazy { PluginTemplateGenerator(AndroidAssetSource(context.assets)) }

    /** Eingebaute Vorlagen, danach die der aktivierten Plugins. */
    override suspend fun listTemplates(): List<ProjectTemplateDescriptor> =
        DESCRIPTORS + contributions.contributions.value.projectTemplates.map { it.toDescriptor() }

    override fun defaultProjectsDirectory(): String =
        projectsDirectoryProvider.getProjectsRoot().absolutePath

    override suspend fun generate(request: ProjectRequest): Result<ProjectHandle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val projectDir = File(request.targetDir, request.projectName)
                require(!projectDir.exists()) { Res.string(R.string.template_verzeichnis_existiert_bereits, projectDir.path) }
                check(projectDir.mkdirs()) { Res.string(R.string.template_verzeichnis_nicht_anlegbar, projectDir.path) }
                val handle = try {
                    val plugin = request.template.plugin
                    if (plugin != null) {
                        pluginGenerator.generate(request, File(plugin.templateDir), projectDir)
                    } else {
                        generator.generate(request, projectDir)
                    }
                } catch (t: Throwable) {
                    projectDir.deleteRecursively()
                    throw t
                }
                if (!request.initGit) return@runCatching handle
                // Git-Fehler dürfen das fertige Projekt nicht verwerfen – sie werden nur gemeldet.
                git.init(projectDir.path, INITIAL_COMMIT_MESSAGE).fold(
                    onSuccess = { r -> handle.copy(gitInitialized = true, gitNote = r.note) },
                    onFailure = { e -> handle.copy(gitInitialized = false, gitNote = Res.string(R.string.template_git_init_fehlgeschlagen, e.message.orEmpty())) },
                )
            }
        }

    private fun Resolved<ProjectTemplateContribution>.toDescriptor(): ProjectTemplateDescriptor {
        val t = item
        return ProjectTemplateDescriptor(
            id = "$pluginId/${t.id}",
            kind = ProjectTemplateKind.PLUGIN,
            supportsJava = ProjectTemplateContribution.LANGUAGE_JAVA in t.languages,
            supportsKotlin = ProjectTemplateContribution.LANGUAGE_KOTLIN in t.languages,
            supportsKts = ProjectTemplateContribution.DSL_KTS in t.gradleDsl,
            supportsGroovy = ProjectTemplateContribution.DSL_GROOVY in t.gradleDsl,
            plugin = PluginTemplateRef(
                pluginId = pluginId,
                label = t.label,
                description = t.description,
                templateDir = File(pluginDir, t.path).path,
                iconFile = t.icon?.let { File(pluginDir, it).path },
                android = t.android,
                minSdk = t.minSdk
            )
        )
    }

    private companion object {
        /** Nachricht des ersten Commits. */
        const val INITIAL_COMMIT_MESSAGE = "Initial commit"

        /** Alle verfügbaren Vorlagen. */
        val DESCRIPTORS = listOf(
            ProjectTemplateDescriptor("no_activity", ProjectTemplateKind.NO_ACTIVITY),
            ProjectTemplateDescriptor("empty_activity", ProjectTemplateKind.EMPTY_ACTIVITY),
            ProjectTemplateDescriptor("cpp_activity", ProjectTemplateKind.CPP_ACTIVITY),
            ProjectTemplateDescriptor("basic_activity", ProjectTemplateKind.BASIC_ACTIVITY),
            ProjectTemplateDescriptor("nav_drawer_activity", ProjectTemplateKind.NAV_DRAWER_ACTIVITY),
            ProjectTemplateDescriptor("bottom_nav_activity", ProjectTemplateKind.BOTTOM_NAV_ACTIVITY),
            ProjectTemplateDescriptor("tabbed_activity", ProjectTemplateKind.TABBED_ACTIVITY),
            ProjectTemplateDescriptor("no_androidx_activity", ProjectTemplateKind.NO_ANDROIDX_ACTIVITY),
            ProjectTemplateDescriptor("compose_activity", ProjectTemplateKind.COMPOSE_ACTIVITY, supportsJava = false),
        )
    }
}
