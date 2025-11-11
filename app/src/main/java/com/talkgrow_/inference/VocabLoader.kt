package com.talkgrow_.inference

import android.content.Context
import org.json.JSONObject

object VocabLoader {
    /** returns (label->id, id->label) */
    fun load(context: Context, file: String = "vocab.json"): Pair<Map<String, Int>, Map<Int, String>> {
        val txt = context.assets.open(file).bufferedReader(Charsets.UTF_8).readText()
        val o = JSONObject(txt)
        val l2i = HashMap<String, Int>(o.length())
        val i2l = HashMap<Int, String>(o.length())
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val id = o.getInt(k)
            l2i[k] = id
            i2l[id] = k
        }
        return l2i to i2l
    }
}
