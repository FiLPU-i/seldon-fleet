package br.edu.uea.seldonfleet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Trilha B - Seguranca e Automacao de Validacoes (Secao 10).
 * Verificacao automatizada executada no pipeline de build/test que falha a integracao
 * caso diretivas de endurecimento do AndroidManifest, isolamento de dominio ou
 * vazamento de identificadores em logs sejam violadas.
 */
class SecurityAuditTest {

    private fun resolveAppDir(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        return if (userDir.name == "app") userDir else File(userDir, "app")
    }

    @Test
    fun `Trilha B - AndroidManifest deve estar endurecido e sem permissao de rede`() {
        val manifestFile = File(resolveAppDir(), "src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml deve existir", manifestFile.exists())

        val content = manifestFile.readText()

        // REQ-01: Proibido acesso a rede
        assertFalse(
            "Violacao REQ-01/Seguranca: Permissao INTERNET nao permitida",
            content.contains("android.permission.INTERNET")
        )

        // Trilha B: Hardening de backup, trafego claro e debuggable
        assertTrue(
            "Violacao Trilha B: android:allowBackup=\"false\" obrigatorio",
            content.contains("android:allowBackup=\"false\"")
        )
        assertTrue(
            "Violacao Trilha B: android:usesCleartextTraffic=\"false\" obrigatorio",
            content.contains("android:usesCleartextTraffic=\"false\"")
        )
        assertFalse(
            "Violacao Trilha B: android:debuggable=\"true\" nao deve ser fixado no manifest",
            content.contains("android:debuggable=\"true\"")
        )
        assertTrue(
            "Violacao Trilha B: MainActivity deve declarar android:exported explicitamente",
            content.contains("android:exported=\"true\"")
        )
    }

    @Test
    fun `REQ-04 - camada domain nao pode conter import android`() {
        val domainDir = File(resolveAppDir(), "src/main/java/br/edu/uea/seldonfleet/domain")
        assertTrue("Diretorio domain/ deve existir", domainDir.exists())

        val violations = domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { idx, line ->
                    if (line.trim().startsWith("import android.")) "${file.name}:${idx + 1}" else null
                }
            }.toList()

        assertTrue(
            "Violacao REQ-04: import android.* encontrado em domain/: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `Trilha B - nenhum identificador de dispositivo pode vazar em chamadas de Log`() {
        val srcDir = File(resolveAppDir(), "src/main/java")
        assertTrue("Diretorio de codigo-fonte deve existir", srcDir.exists())

        val violations = mutableListOf<String>()
        srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    if (isSensitiveLogViolation(line)) {
                        violations.add("${file.name}:${idx + 1} -> ${line.trim()}")
                    }
                }
            }

        assertTrue(
            "Violacao Trilha B (LGPD/Logs): Vazamento de identificador detectado: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `Trilha B - verificador detecta vazamento real e ignora falsos positivos conhecidos`() {
        // Caso 1: Vazamento real em Log do Android -> DEVE falhar na auditoria
        val realLeak = """Log.d("FleetTracker", "Processando aparelho " + device.id)"""
        assertTrue("Deveria detectar vazamento real de device.id", isSensitiveLogViolation(realLeak))

        // Caso 2: Falso positivo 1 - Comentario documentando o que nao fazer -> NAO deve acusar erro
        val commentedLine = """// Exemplo proibido: Log.d("TAG", device.id)"""
        assertFalse("Nao deve acusar falso positivo em comentarios", isSensitiveLogViolation(commentedLine))

        // Caso 3: Falso positivo 2 - Variavel matematicamente chamada 'id' ou texto sem dado pessoal
        // ou linha explicitamente auditada com @SecurityAuditIgnore -> NAO deve acusar erro
        val auditedSafeLine = """Log.i("Audit", "Contagem concluida") // @SecurityAuditIgnore"""
        assertFalse("Nao deve acusar falso positivo em linha auditada sem PII", isSensitiveLogViolation(auditedSafeLine))
    }

    companion object {
        private val LOG_CALL_REGEX = Regex("""\b(Log\.[vdiwe]|println|System\.out\.print)\s*\(""")
        private val SENSITIVE_ID_REGEX = Regex("""\b(device\.id|dto\.id|deviceId)\b|DEV-\d{4}""")

        fun isSensitiveLogViolation(line: String): Boolean {
            val trimmed = line.trim()
            // Tratamento explicito de falsos positivos: ignora comentarios e supressoes auditadas
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                return false
            }
            if (trimmed.contains("@SecurityAuditIgnore")) {
                return false
            }
            val codeWithoutInlineComment = trimmed.substringBefore("//")
            return LOG_CALL_REGEX.containsMatchIn(codeWithoutInlineComment) &&
                SENSITIVE_ID_REGEX.containsMatchIn(codeWithoutInlineComment)
        }
    }
}
