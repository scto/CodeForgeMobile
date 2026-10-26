package com.codeforge.core.data

import android.content.Context
import android.content.ContextWrapper
import com.codeforge.core.data.repository.ProjectsDirectoryProviderImpl
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectsDirectoryProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeContext: Context
    private lateinit var testFilesDir: File
    private lateinit var testExternalFilesDir: File

    @Before
    fun setup() {
        testFilesDir = tempFolder.newFolder("files")
        testExternalFilesDir = tempFolder.newFolder("externalFiles")

        fakeContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = testFilesDir
            override fun getExternalFilesDir(type: String?): File = testExternalFilesDir
            override fun getPackageName(): String = "com.codeforge.test"
        }
    }

    @Test
    fun `constant projects directory name matches official standard`() {
        assertEquals("CodeForgeMobileProjects", ProjectsDirectoryProvider.PROJECTS_DIR_NAME)
    }

    @Test
    fun `sanitizeProjectName handles special characters, cfm and blanks`() {
        val provider = object : ProjectsDirectoryProviderImpl(fakeContext) {
            override fun isExternalStorageUsable() = false
        }

        assertEquals("My_App_Test", provider.sanitizeProjectName("My App / Test!"))
        assertEquals("project", provider.sanitizeProjectName(""))
        assertEquals("project", provider.sanitizeProjectName("..."))
        assertEquals("project", provider.sanitizeProjectName(".cfm"))
        assertEquals("project", provider.sanitizeProjectName("cfm"))
        assertEquals("Valid-Name.1", provider.sanitizeProjectName("Valid-Name.1"))
    }

    @Test
    fun `resolveProjectPath rejects directory traversal escape attempts`() {
        val provider = object : ProjectsDirectoryProviderImpl(fakeContext) {
            override fun isExternalStorageUsable() = false
            override fun getProjectsRoot() = testFilesDir
        }

        // Gültiger Name
        val resolved = provider.resolveProjectPath("HelloWorld")
        assertEquals(File(testFilesDir, "HelloWorld").canonicalPath, resolved.canonicalPath)

        // Traversal Attempt
        val sanitized = provider.sanitizeProjectName("../../../etc/passwd")
        assertFalse(sanitized.contains(".."))
        val safeResolved = provider.resolveProjectPath("../../../etc/passwd")
        assertTrue(safeResolved.canonicalPath.startsWith(testFilesDir.canonicalPath))
    }

    @Test
    fun `isSubpathOfProjectsRoot detects inside vs outside files`() {
        val provider = object : ProjectsDirectoryProviderImpl(fakeContext) {
            override fun isExternalStorageUsable() = false
            override fun getProjectsRoot() = testFilesDir
        }

        val childFile = File(testFilesDir, "MyProject/src/Main.kt")
        assertTrue(provider.isSubpathOfProjectsRoot(childFile))

        val outsideFile = tempFolder.newFile("outside.txt")
        assertFalse(provider.isSubpathOfProjectsRoot(outsideFile))
    }
}
