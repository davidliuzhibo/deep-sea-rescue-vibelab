package com.vrgames.deeprescue.ui.rescue.components

import com.google.gson.JsonObject
import com.google.gson.JsonParser

class PanelContract(text:String) {
    val document=JsonParser.parseString(text).asJsonObject
    private val nodes=document.getAsJsonArray("nodes").associate{val n=it.asJsonObject;n.get("id").asString to n}
    private val roots=document.getAsJsonArray("states").associate{val n=it.asJsonObject;n.get("id").asString to n.getAsJsonObject("surfaceRoots").get("controls").asString}
    fun action(id:String):JsonObject?=document.getAsJsonArray("actions").firstOrNull{it.asJsonObject.str("id")==id}?.asJsonObject?.obj("payload")
    fun actionsOn(page:String):Set<String> {
        fun walk(n:JsonObject):List<String> = listOfNotNull(n.obj("events")?.str("click-action"))+children(n).flatMap{walk(it)}
        return walk(root(page)).toSet()
    }
    fun root(state:String)=nodes.getValue(roots.getValue(state))
    fun children(node:JsonObject)=node.getAsJsonArray("children")?.map{nodes.getValue(it.asString)}?:emptyList()
    /** Apply the accepted compact overrides to the actual host width, not each grid cell. */
    fun layout(node:JsonObject,widthDp:Float):JsonObject? {
        val result=node.obj("layout")?.deepCopy()?:return null
        document.getAsJsonArray("responsiveRules")?.forEach { value ->
            val rule=value.asJsonObject
            if(rule.str("surfaceId")=="controls"&&widthDp<=(rule.obj("when")?.number("maxWidthDp")?:0f)) {
                rule.getAsJsonArray("overrides")?.forEach { item ->
                    val override=item.asJsonObject
                    if(override.str("nodeId")==node.str("id"))override.obj("layout")?.entrySet()?.forEach{result.add(it.key,it.value.deepCopy())}
                }
            }
        }
        return result
    }
    fun fallback(path:String):Any? {
        var value:com.google.gson.JsonElement=document.getAsJsonObject("dataCases").getAsJsonObject("normal")
        for(part in path.split('.'))value=(value as? JsonObject)?.get(part)?:return null
        return if(value.isJsonPrimitive) {val p=value.asJsonPrimitive;if(p.isBoolean)p.asBoolean else p.asString} else null
    }
    fun text(n:JsonObject,read:(String)->Any?):String {
        val bound=n.obj("bindings")?.str("content")?:n.obj("bindings")?.str("text")
        if(bound!=null)return (read(bound)?:fallback(bound)?:"").toString()
        return n.str("content")?:n.obj("props")?.str("text")?:""
    }
    fun hidden(n:JsonObject,read:(String)->Any?)=n.obj("bindings")?.str("hidden")?.let{read(it)==true}?:false
    fun disabled(n:JsonObject,read:(String)->Any?)=n.obj("bindings")?.str("disabled")?.let{read(it)==true}?:false
}
internal fun JsonObject.obj(k:String)=get(k)?.takeIf{it.isJsonObject}?.asJsonObject
internal fun JsonObject.str(k:String)=get(k)?.takeIf{it.isJsonPrimitive}?.asString
internal fun JsonObject.number(k:String,default:Float=0f)=get(k)?.takeIf{it.isJsonPrimitive&&it.asJsonPrimitive.isNumber}?.asFloat?:default
