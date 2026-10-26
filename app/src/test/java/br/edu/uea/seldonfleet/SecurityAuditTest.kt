package br.edu.uea.seldonfleet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Trilha B - Seguranca e Automacao de Validacoes (Secao 10).
 * Verificacao estatica automatizada executada na pipeline de build/test.
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
    fun `Trilha B - nenhum identificador de dispositivo pode vazar em chamadas de Log no codigo-fonte`() {
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
    fun `Trilha B - deteccao de vazamentos simples, multilinha, interpolados e alias local`() {
        val directLeak = """Log.d("FleetTracker", "Processando " + device.id)"""
        assertTrue(findSensitiveLogViolationsInSource(directLeak).isNotEmpty())

        val templateLeak = """Log.e("FleetTracker", "Falha no aparelho $" + "{device.id}")"""
        assertTrue(findSensitiveLogViolationsInSource(templateLeak).isNotEmpty())

        val multilineLeak = """
            fun debugDevice() {
                android.util.Log.w(
                    "FleetAudit",
                    "Falha no dispositivo: " +
                        device.id
                )
            }
        """.trimIndent()
        assertTrue(findSensitiveLogViolationsInSource(multilineLeak).isNotEmpty())

        val localAliasLeak = """
            fun logViaAlias(device: Device) {
                val trackedTarget = device.id
                Timber.i("Monitorando %s", trackedTarget)
            }
        """.trimIndent()
        assertTrue(
            "Deve detectar quando device.id eh copiado para variavel local e logado em seguida",
            findSensitiveLogViolationsInSource(localAliasLeak).isNotEmpty()
        )
    }

    @Test
    fun `Trilha B - politica estrita de supressao e documentacao dos limites do verificador`() {
        val bareSuppression = """Log.i("Audit", "Valor: " + deviceId) // @SecurityAuditIgnore"""
        assertTrue(
            "Supressao sem reason formal revisavel deve ser rejeitada",
            findSensitiveLogViolationsInSource(bareSuppression).isNotEmpty()
        )

        val abusiveSuppression = """
            Log.d("TAG", device.id) // @SecurityAuditIgnore(reason="SEC-999: tentativa de burlar regra")
        """.trimIndent()
        assertTrue(
            "Nenhuma supressao pode autorizar o vazamento direto de device.id ou DEV-XXXX",
            findSensitiveLogViolationsInSource(abusiveSuppression).isNotEmpty()
        )

        val reviewedFalsePositive = """
            Log.i("SchemaCheck", "Campo validado: deviceId") // @SecurityAuditIgnore(reason="SEC-101: string literal de cabecalho de schema, sem valor de instancia")
        """.trimIndent()
        assertTrue(
            "Falso positivo de string literal com justificativa formal SEC-XXX deve ser aceito",
            findSensitiveLogViolationsInSource(reviewedFalsePositive).isEmpty()
        )

        val opaqueExternalCall = """
            Log.d("Metrics", "Total processado: 30")
        """.trimIndent()
        assertTrue(findSensitiveLogViolationsInSource(opaqueExternalCall).isEmpty())
    }

    companion object {
        private val BLOCK_COMMENT_REGEX = Regex("""/\*[\s\S]*?\*/""")
        private val LINE_COMMENT_REGEX = Regex("""//.*$""", RegexOption.MULTILINE)

        private val REVIEWED_SUPPRESSION_REGEX =
            Regex("""@SecurityAuditIgnore\(\s*reason\s*=\s*"SEC-\d{3,}:[^"]{20,}"\s*\)""")

        private val HARD_PII_INSTANCE_REGEX =
            Regex("""(?:device\.id|dto\.id|item\.device\.id|DEV-\d{4})""")

        private val SENSITIVE_ID_REGEX =
            Regex("""(?:device\.id|dto\.id|item\.device\.id|\bdeviceId\b|DEV-\d{4})""")

        private val LOCAL_ALIAS_DECLARATION_REGEX =
            Regex("""\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?:device\.id|dto\.id|item\.device\.id|deviceId)\b""")

        private val LOG_STATEMENT_MULTILINE_REGEX = Regex(
            """\b(?:(?:android\.util\.)?Log\.(?:[vdiwe]|wtf)|Timber\.(?:[vdiwe]|wtf)|Logger\.(?:[vdiwe]|info|warn|error|debug)|println|System\.(?:out|err)\.print(?:ln)?)\s*\([^;{}]*?\)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        fun findSensitiveLogViolationsInSource(sourceCode: String): List<String> {
            val withoutBlockComments = BLOCK_COMMENT_REGEX.replace(sourceCode, "")

            val aliasedVars = LOCAL_ALIAS_DECLARATION_REGEX.findAll(withoutBlockComments)
                .map { it.groupValues[1] }
                .toSet()

            val sanitizedLines = withoutBlockComments.lines().map { line ->
                val hasValidTicketReason = REVIEWED_SUPPRESSION_REGEX.containsMatchIn(line)
                val codePart = line.substringBefore("//")
                if (hasValidTicketReason && !HARD_PII_INSTANCE_REGEX.containsMatchIn(codePart)) {
                    ""
                } else {
                    codePart
                }
            }.joinToString("\n")

            val cleanCode = LINE_COMMENT_REGEX.replace(sanitizedLines, "")

            val violations = mutableListOf<String>()
            LOG_STATEMENT_MULTILINE_REGEX.findAll(cleanCode).forEach { match ->
                val callText = match.value
                val hasDirectId = SENSITIVE_ID_REGEX.containsMatchIn(callText)
                val hasAliasedId = aliasedVars.any { alias ->
                    Regex("""\b${Regex.escape(alias)}\b""").containsMatchIn(callText)
                }
                if (hasDirectId || hasAliasedId) {
                    violations.add(callText.replace(Regex("""\s+"""), " ").trim())
                }
            }
            return violations
        }
    }
}
