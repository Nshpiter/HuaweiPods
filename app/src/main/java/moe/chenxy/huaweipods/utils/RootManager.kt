package moe.chenxy.huaweipods.utils

import java.util.concurrent.TimeUnit

object RootManager {
    private val packageNameRegex = Regex("^[A-Za-z0-9_.]+$")
    private val restartOrder = listOf(
        "com.heytap.accessory",
        "com.heytap.mydevices",
        "com.oplus.wirelesssettings",
        "com.huawei.smartaudio",
        "com.android.bluetooth",
    )

    fun restartPackages(packages: Collection<String>): Boolean {
        val targets = orderedRestartTargets(packages)
        if (targets.isEmpty()) return false

        return runCatching {
            // Root 管理器的首次授权弹窗不应消耗真正重启作用域的短超时。
            runRootCommand("exit 0", ROOT_AUTH_TIMEOUT_SECONDS) &&
                runRootCommand(buildRestartCommand(targets), RESTART_TIMEOUT_SECONDS)
        }.getOrDefault(false)
    }

    private fun runRootCommand(command: String, timeoutSeconds: Long): Boolean {
        val process = ProcessBuilder("su", "-c", command).start()
        return if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(PROCESS_CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            false
        } else {
            process.exitValue() == 0
        }
    }

    /** 先停止界面消费者，最后重启蓝牙生产者，避免新旧 Hook 在重连期间交叉工作。 */
    internal fun orderedRestartTargets(packages: Collection<String>): List<String> {
        val safeTargets = packages
            .filter { it.matches(packageNameRegex) }
            .distinct()
        val knownTargets = restartOrder.filter(safeTargets::contains)
        val additionalTargets = safeTargets.filterNot(restartOrder::contains)
        return knownTargets.dropLastWhile { it == BLUETOOTH_PACKAGE } +
            additionalTargets +
            knownTargets.takeLastWhile { it == BLUETOOTH_PACKAGE }
    }

    /**
     * 仅扫描一次 /proc 快照旧 PID，再以消费者到生产者的顺序批量终止。
     * 不使用 `am force-stop`，避免把依赖动态 Receiver 的系统包置为 stopped。
     */
    internal fun buildRestartCommand(targets: List<String>): String {
        val quotedTargets = targets.joinToString(" ") { "'$it'" }
        return """
            scope_pid_matches() {
              sm_pid="${'$'}1"
              sm_target="${'$'}2"
              case "${'$'}sm_pid" in ''|*[!0-9]*) return 1 ;; esac
              [ -r "/proc/${'$'}sm_pid/cmdline" ] || return 1
              sm_name=""
              IFS= read -r -d '' sm_name < "/proc/${'$'}sm_pid/cmdline" 2>/dev/null || return 1
              case "${'$'}sm_name" in
                "${'$'}sm_target"|"${'$'}sm_target":*) return 0 ;;
                *) return 1 ;;
              esac
            }

            scope_capture() {
              for sc_proc in /proc/[0-9]*; do
                [ -r "${'$'}sc_proc/cmdline" ] || continue
                sc_name=""
                IFS= read -r -d '' sc_name < "${'$'}sc_proc/cmdline" 2>/dev/null || continue
                for sc_target in "${'$'}@"; do
                  case "${'$'}sc_name" in
                    "${'$'}sc_target"|"${'$'}sc_target":*)
                      printf '%s:%s\n' "${'$'}{sc_proc##*/}" "${'$'}sc_target"
                      break
                      ;;
                  esac
                done
              done
            }

            restart_scopes() {
              scope_snapshot="${'$'}(scope_capture "${'$'}@")"

              # 快照只取一次；TERM 仍按消费者 -> 蓝牙生产者的顺序发送。
              for rs_target in "${'$'}@"; do
                for rs_entry in ${'$'}scope_snapshot; do
                  rs_pid="${'$'}{rs_entry%%:*}"
                  rs_entry_target="${'$'}{rs_entry#*:}"
                  [ "${'$'}rs_entry_target" = "${'$'}rs_target" ] || continue
                  scope_pid_matches "${'$'}rs_pid" "${'$'}rs_target" &&
                    kill -15 "${'$'}rs_pid" >/dev/null 2>&1 || true
                done
              done

              # 所有旧 PID 共用一个短轮询窗口，不再对每个包等待数秒。
              rs_any_alive=0
              for rs_attempt in 1 2 3 4 5 6; do
                rs_any_alive=0
                for rs_entry in ${'$'}scope_snapshot; do
                  rs_pid="${'$'}{rs_entry%%:*}"
                  rs_target="${'$'}{rs_entry#*:}"
                  if scope_pid_matches "${'$'}rs_pid" "${'$'}rs_target"; then
                    rs_any_alive=1
                    break
                  fi
                done
                [ "${'$'}rs_any_alive" -eq 0 ] && break
                sleep 0.2
              done

              rs_killed=0
              for rs_entry in ${'$'}scope_snapshot; do
                rs_pid="${'$'}{rs_entry%%:*}"
                rs_target="${'$'}{rs_entry#*:}"
                if scope_pid_matches "${'$'}rs_pid" "${'$'}rs_target"; then
                  kill -9 "${'$'}rs_pid" >/dev/null 2>&1 || true
                  rs_killed=1
                fi
              done
              [ "${'$'}rs_killed" -eq 0 ] || sleep 0.2

              status=0
              for rs_entry in ${'$'}scope_snapshot; do
                rs_pid="${'$'}{rs_entry%%:*}"
                rs_target="${'$'}{rs_entry#*:}"
                scope_pid_matches "${'$'}rs_pid" "${'$'}rs_target" && status=1
              done
              return "${'$'}status"
            }
            restart_scopes $quotedTargets
        """.trimIndent()
    }

    private const val BLUETOOTH_PACKAGE = "com.android.bluetooth"
    private const val ROOT_AUTH_TIMEOUT_SECONDS = 90L
    private const val RESTART_TIMEOUT_SECONDS = 10L
    private const val PROCESS_CLEANUP_TIMEOUT_SECONDS = 2L
}
