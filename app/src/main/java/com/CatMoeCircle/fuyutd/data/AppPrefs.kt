package com.CatMoeCircle.fuyutd.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局 SharedPreferences 入口，需在 Activity / Application 启动时调用一次 [init]。
 */
object AppPrefs {

    private const val FILE_NAME = "settings"

    @Volatile
    private var instance: SharedPreferences? = null

    fun init(context: Context) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = context.applicationContext
                        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                }
            }
        }
    }

    fun prefs(): SharedPreferences = instance ?: error("AppPrefs 尚未初始化")
}
