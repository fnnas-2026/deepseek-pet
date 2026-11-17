package com.ferry.pet.phone

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * B 档操作确认闸门。
 *
 * 对外动作前由 [request] 挂起等待用户在确认卡上选择：
 *  - 允许一次：本次放行，下次仍需确认；
 *  - 本轮允许：当前进程内连续放行，直到 [reset]；
 *  - 取消：拒绝本次动作。
 *
 * 状态只保存在内存，App 进程结束即重置，避免长期放行误触敏感操作。
 */
object ActionGate {

    enum class Decision(val allow: Boolean) { ONCE(true), SESSION(true), DENY(false) }

    /** 本轮会话内持续放行。 */
    private val sessionAllow = AtomicBoolean(false)

    /** 当前待决请求；确认卡 UI 轮询 [pending] 并调用 [resolve]。 */
    private val pending = AtomicReference<Pending?>(null)

    class Pending(val description: String, val latch: java.util.concurrent.CountDownLatch,
                  val decision: AtomicReference<Decision>)

    val awaiting: Boolean get() = pending.get() != null

    /** 取走当前待决请求（确认卡展示后调用）。 */
    fun takePending(): Pending? = pending.getAndSet(null)

    fun resolve(decision: Decision) {
        pending.getAndSet(null)?.let {
            it.decision.set(decision)
            it.latch.countDown()
        }
    }

    /**
     * 请求一次动作许可。已处于「本轮允许」则直接放行；否则挂起等待确认卡回填。
     * 10 秒内无操作按拒绝处理。
     */
    fun request(describe: String): Boolean {
        if (sessionAllow.get()) return true
        val latch = java.util.concurrent.CountDownLatch(1)
        val decision = AtomicReference(Decision.DENY)
        pending.set(Pending(describe, latch, decision))
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        pending.compareAndSet(pending.get(), null)
        return when (decision.get()) {
            Decision.ONCE -> true
            Decision.SESSION -> { sessionAllow.set(true); true }
            Decision.DENY -> false
        }
    }

    fun reset() {
        sessionAllow.set(false)
        pending.getAndSet(null)
    }
}
