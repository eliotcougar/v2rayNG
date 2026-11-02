package com.v2ray.ang.handler

import android.content.Intent
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.implementation.StubMethod
import net.bytebuddy.matcher.ElementMatchers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

/** In-memory MMKV boundary: production manager transactions and serialization still execute. */
class MmkvTestStore : AutoCloseable {
    class Store {
        val values = linkedMapOf<String, Any>()
        var failWrite: (String) -> Boolean = { false }
        var failRemove = false
        var lockDepth = 0
            private set
        val mmkv: MMKV = Mockito.mock(mmkvClass)

        init {
            whenever(mmkv.containsKey(any())).thenAnswer { it.getArgument<String>(0) in values }
            whenever(mmkv.decodeString(any())).thenAnswer { values[it.getArgument<String>(0)] as? String }
            whenever(mmkv.decodeBool(any(), any())).thenAnswer {
                values[it.getArgument<String>(0)] as? Boolean ?: it.getArgument<Boolean>(1)
            }
            whenever(mmkv.allKeys()).thenAnswer { values.keys.toTypedArray() }
            whenever(mmkv.encode(any<String>(), any<String>())).thenAnswer {
                val key = it.getArgument<String>(0)
                if (failWrite(key)) false else { values[key] = it.getArgument<String>(1); true }
            }
            whenever(mmkv.encode(any<String>(), any<Boolean>())).thenAnswer {
                val key = it.getArgument<String>(0)
                if (failWrite(key)) false else { values[key] = it.getArgument<Boolean>(1); true }
            }
            doAnswer {
                if (!failRemove) values.remove(it.getArgument<String>(0))
                null
            }.whenever(mmkv).remove(any())
            doAnswer {
                if (!failRemove) it.getArgument<Array<String>>(0).forEach(values::remove)
                null
            }.whenever(mmkv).removeValuesForKeys(any())
            doAnswer { values.clear(); null }.whenever(mmkv).clearAll()
            doAnswer { lockDepth++; null }.whenever(mmkv).lock()
            doAnswer { lockDepth--; null }.whenever(mmkv).unlock()
        }
    }

    private val restore = mutableListOf<() -> Unit>()
    val main = install("mainStorage")
    val profiles = install("profileFullStorage")
    val raw = install("serverRawStorage")
    val affiliations = install("serverAffStorage")
    val subscriptions = install("subStorage")
    val settings = install("settingsStorage")
    val selectedProfileChanges = mutableListOf<String?>()
    var notificationsWhileLocked = 0
        private set
    var onSelectedProfileChanged: () -> Unit = {}

    init {
        val field = LogUtil::class.java.getDeclaredField("cachedMinPriority").apply { isAccessible = true }
        val previous = field.get(null)
        field.set(null, Int.MAX_VALUE)
        restore += { field.set(null, previous) }

        val applicationField = AngApplication::class.java.getDeclaredField("application")
            .apply { isAccessible = true }
        val previousApplication = applicationField.get(null)
        val application = Mockito.mock(AngApplication::class.java)
        applicationField.set(null, application)
        restore += { applicationField.set(null, previousApplication) }

        val intents = Mockito.mockConstruction(Intent::class.java) { intent, _ ->
            var messageKey = 0
            whenever(intent.putExtra(any<String>(), any<Int>())).thenAnswer {
                messageKey = it.getArgument(1)
                intent
            }
            whenever(intent.getIntExtra(any(), any())).thenAnswer { messageKey }
        }
        restore += { intents.close() }
        doAnswer {
            if (it.getArgument<Intent>(0).getIntExtra("key", 0) == AppConfig.MSG_SELECTED_PROFILE_CHANGED) {
                if (main.lockDepth != 0) notificationsWhileLocked++
                selectedProfileChanges += MmkvManager.getSelectServer()
                onSelectedProfileChanged()
            }
            null
        }.whenever(application).sendBroadcast(any())
    }

    private fun install(name: String): Store {
        val delegate = MmkvManager::class.java.getDeclaredField("${name}\$delegate")
            .apply { isAccessible = true }.get(null)
        val value = delegate.javaClass.getDeclaredField("_value").apply { isAccessible = true }
        val previous = value.get(delegate)
        val store = Store()
        value.set(delegate, store.mmkv)
        restore += { value.set(delegate, previous) }
        return store
    }

    override fun close() = restore.asReversed().forEach { it() }

    companion object {
        // Mockito cannot intercept JNI methods. Override them before installing normal mock answers.
        private val mmkvClass = ByteBuddy()
            .subclass(MMKV::class.java, ConstructorStrategy.Default.NO_CONSTRUCTORS)
            .method(ElementMatchers.isNative())
            .intercept(StubMethod.INSTANCE)
            .make().load(MMKV::class.java.classLoader).loaded
    }
}
