package br.edu.uea.seldonfleet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Trilha B - Seguranca e Automacao de Validacoes (Secao 10).
 * Verificacao automatizada executada no pipeline de build/test que falha a integracao
 * caso diretivas de endurecimento do AndroidManifest, isolamento de dominio ou
 * vazamento de identificadores em logs (incluindo chamadas multilinha e loggers alternativos)
 * sejam violadas.
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

        assertFalse(
            "Violacao REQ-01/Seguranca: Permissao INTERNET nao permitida",
            content.contains("android.permission.INTERNET")
        )
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
    fun `Trilha B - nenhum identificador de dispositivo pode vazar em chamadas de Log simples ou multilinha`() {
        val srcDir = File(resolveAppDir(), "src/main/java")
        assertTrue("Diretorio de codigo-fonte deve existir", srcDir.exists())

        val violations = mutableListOf<String>()
        srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val fileViolations = findSensitiveLogViolationsInSource(file.readText())
                fileViolations.forEach { snippet ->
                    violations.add("${file.name} -> $snippet")
                }
            }

        assertTrue(
            "Violacao Trilha B (LGPD/Logs): Vazamento de identificador detectado: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `Trilha B - verificador detecta vazamentos simples, multilinha, loggers alternativos e valida falsos positivos`() {
        // Caso 1: Vazamento direto em Log.d -> DEVE detectar
        val directLeak = """Log.d("FleetTracker", "Processando aparelho " + device.id)"""
        assertTrue(findSensitiveLogViolationsInSource(directLeak).isNotEmpty())

        // Caso 2: Vazamento distribuido em multiplas linhas -> DEVE detectar
        val multilineLeak = """
            fun debugDevice() {
                android.util.Log.w(
                    "FleetAudit",
                    "Falha no dispositivo: " +
                        device.id
                )
            }
        """.trimIndent()
        assertTrue(
            "Deve detectar vazamento mesmo quebrado em varias linhas",
            findSensitiveLogViolationsInSource(multilineLeak).isNotEmpty()
        )

        // Caso 3: Chamadas alternativas de logging (Timber, Logger, System.err) -> DEVE detectar
        val timberLeak = """Timber.e("Erro em %s", deviceId)"""
        assertTrue(
            "Deve detectar vazamento em bibliotecas alternativas como Timber/Logger",
            findSensitiveLogViolationsInSource(timberLeak).isNotEmpty()
        )

        // Caso 4: Supressao sem justificativa revisavel -> DEVE continuar falhando!
        val unjustifiedSuppression = """Log.d("TAG", device.id) // @SecurityAuditIgnore"""
        assertTrue(
            "Supressao sem motivo explicito e sem aprovacao nao deve isentar vazamento real",
            findSensitiveLogViolationsInSource(unjustifiedSuppression).isNotEmpty()
        )

        // Caso 5: Falso positivo em comentario de bloco ou de linha -> NAO deve acusar erro
        val commentedCode = """
            /*
             * Exemplo proibido na documentacao:
             * Log.d("TAG", device.id)
             */
            // println(device.id)
        """.trimIndent()
        assertTrue(
            "Comentarios nao devem gerar falso positivo",
            findSensitiveLogViolationsInSource(commentedCode).isEmpty()
        )

        // Caso 6: Falso positivo em log de métrica agregada com revisao documentada (sem PII real)
        val reviewedAggregateLog = """
            Log.i("Audit", "Total avaliado: 30") // @SecurityAuditIgnore(reason="Metrica agregada sem ID individual - Revisado SecOps")
        """.trimIndent()
        assertTrue(
            "Linha sem ID individual e com justificativa auditavel nao deve falhar",
            findSensitiveLogViolationsInSource(reviewedAggregateLog).isEmpty()
        )
    }

    companion object {
        private val BLOCK_COMMENT_REGEX = Regex("""/\*[\s\S]*?\*/""")
        private val LINE_COMMENT_REGEX = Regex("""//.*$""", RegexOption.MULTILINE)
        private val REVIEWED_SUPPRESSION_REGEX =
            Regex("""@SecurityAuditIgnore\(\s*reason\s*=\s*"[^"]{10,}"\s*\)""")

        // Cobre android.util.Log, Log.*, Timber.*, Logger.*, println, print, System.out/err
        private val LOG_STATEMENT_MULTILINE_REGEX = Regex(
            """\b(?:(?:android\.util\.)?Log\.(?:[vdiwe]|wtf)|Timber\.(?:[vdiwe]|wtf)|Logger\.(?:[vdiwe]|info|warn|error|debug)|println|print|System\.(?:out|err)\.print(?:ln)?)\s*\([^;{}]*?\)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        private val SENSITIVE_ID_REGEX =
            Regex("""\b(?:device\.id|dto\.id|item\.device\.id|deviceId)\b|DEV-\d{4}""")

        fun findSensitiveLogViolationsInSource(sourceCode: String): List<String> {
            // 1. Remove comentarios de bloco /* ... */
            val withoutBlockComments = BLOCK_COMMENT_REGEX.replace(sourceCode, "")

            // 2. Remove linhas que tenham supressao revisada (@SecurityAuditIgnore(reason="..."))
            //    APENAS se nao contiverem referencia direta a identificadores de dispositivo
            val filteredLines = withoutBlockComments.lines().map { line ->
                val hasReviewedReason = REVIEWED_SUPPRESSION_REGEX.containsMatchIn(line)
                val codePart = line.substringBefore("//")
                if (hasReviewedReason && !SENSITIVE_ID_REGEX.containsMatchIn(codePart)) {
                    ""
                } else {
                    codePart
                }
            }.joinToString("\n")

            val withoutComments = LINE_COMMENT_REGEX.replace(filteredLines, "")

            // 3. Inspeciona todas as chamadas de log (mesmo que distribuidas em varias linhas)
            val violations = mutableListOf<String>()
            LOG_STATEMENT_MULTILINE_REGEX.findAll(withoutComments).forEach { match ->
                val callText = match.value
                if (SENSITIVE_ID_REGEX.containsMatchIn(callText)) {
                    violations.add(callText.replace(Regex("""\s+"""), " ").trim())
                }
            }
            return violations
        }
    }
}
