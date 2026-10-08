package com.codeforge.libs.template_engine

import android.content.Context
import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.ProjectTemplateKind
import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.domain.repository.TemplateEngineRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateEngineRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val git: GitRepository,
) : TemplateEngineRepository {

    private val generator by lazy { ProjectGenerator(AndroidAssetSource(context.assets)) }

    override suspend fun listTemplates(): List<ProjectTemplateDescriptor> = DESCRIPTORS

    override fun defaultProjectsDirectory(): String =
        File(context.filesDir, "projects").absolutePath

    override suspend fun generate(request: ProjectRequest): Result<ProjectHandle> =
        withContext(Dispatchers.IO) {
            runCatching {
                val projectDir = File(request.targetDir, request.projectName)
                require(!projectDir.exists()) { Res.string(R.string.template_verzeichnis_existiert_bereits, projectDir.path) }
                check(projectDir.mkdirs()) { Res.string(R.string.template_verzeichnis_nicht_anlegbar, projectDir.path) }
                val handle = try {
                    generator.generate(request, projectDir)
                } catch (t: Throwable) {
                    projectDir.deleteRecursively()
                    throw t
                }
                if (!request.initGit) return@runCatching handle
                // Git-Fehler dürfen das fertige Projekt nicht verwerfen – sie werden nur gemeldet.
                git.init(projectDir.path, INITIAL_COMMIT_MESSAGE).fold(
                    onSuccess = { r -> handle.copy(gitInitialized = true, gitNote = r.note) },
                    onFailure = { e -> handle.copy(gitInitialized = false, gitNote = Res.string(R.string.template_git_init_fehlgeschlagen, e.message)) },
                )
            }
        }

    private companion object {
        const val INITIAL_COMMIT_MESSAGE = "Initial commit"

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
