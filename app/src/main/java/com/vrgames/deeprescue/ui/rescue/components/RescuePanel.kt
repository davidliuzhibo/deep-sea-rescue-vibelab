package com.vrgames.deeprescue.ui.rescue.components

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.vrgames.deeprescue.ui.theme.RescueColors

/** Direct executable-IR restoration, with native controls and no separate UI truth. */
private val LocalPanelWidth=staticCompositionLocalOf {960f}
@Composable fun RescuePanel(contract:PanelContract,page:String,read:(String)->Any?,send:(String)->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp)).background(androidx.compose.ui.graphics.Color(0xF2141C22))) {
    CompositionLocalProvider(LocalPanelWidth provides maxWidth.value) {
    key(page) {
        if(page=="placement")Box(Modifier.fillMaxSize().padding(40.dp)){Text("正在摆放观察窗…",fontSize=32.sp)}
        else PanelNode(contract,contract.root(page),read,send,Modifier.fillMaxSize())
        if(read("ui.canBack")==true)Button(onClick={send(com.vrgames.deeprescue.ui.rescue.RescueViewModel.BACK)},modifier=Modifier.align(Alignment.TopEnd).padding(16.dp)){Text("返回",fontSize=22.sp)}
    }
    }
    }
}
@Composable private fun PanelNode(c:PanelContract,n:JsonObject,read:(String)->Any?,send:(String)->Unit,modifier:Modifier=Modifier) {
    if(c.hidden(n,read))return
    val l=c.layout(n,LocalPanelWidth.current);val gap=l?.number("gapDp")?:0f
    when(n.str("kind")) {
        "layout"->{
            val children=c.children(n).filterNot{c.hidden(it,read)};if(children.isEmpty())return
            var m=modifier.fillMaxWidth().padding((l?.number("paddingDp")?:0f).dp)
            val mode=l?.str("mode")
            if(mode=="scroll")m=m.verticalScroll(rememberScrollState())
            when(mode) {
                "grid"->Box(m) {
                    val desired=(l?.number("columns",2f)?:2f).toInt()
                    val cols=desired.coerceAtLeast(1)
                    Column(verticalArrangement=Arrangement.spacedBy(gap.dp)) {
                        children.chunked(cols).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(gap.dp)){
                            row.forEach{PanelNode(c,it,read,send,Modifier.weight(1f))}
                            repeat(cols-row.size){androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))}
                        }}
                    }
                }
                "row"->Row(m,horizontalArrangement=Arrangement.spacedBy(gap.dp)){children.forEach{PanelNode(c,it,read,send,Modifier.weight(1f))}}
                else->Column(m,verticalArrangement=Arrangement.spacedBy(gap.dp)){
                    children.forEach{child->val grow=child.obj("layout")?.number("grow")?:0f;PanelNode(c,child,read,send,if(grow>0)Modifier.weight(grow)else Modifier)}
                }
            }
        }
        "spatialui"->{
            require(n.str("component")=="sui-button")
            val action=n.obj("events")?.str("click-action")?:return
            val enabled=!c.disabled(n,read)
            // The button that moves on to the next thing stands out from the rest.
            val key=enabled&&(action=="result-next-action"||action=="start-action")
            Button(onClick={send(action)},enabled=enabled,colors=if(key)com.pico.spatial.ui.design.ButtonDefaults.buttonColors(androidx.compose.ui.graphics.Color(0xFFF4C542),androidx.compose.ui.graphics.Color(0xFF2A1F00))else com.pico.spatial.ui.design.ButtonDefaults.buttonColors(),modifier=modifier.fillMaxWidth().heightIn(min=(l?.number("minHeightDp",56f)?:56f).dp)) {
                Text(c.text(n,read),fontSize=20.sp,lineHeight=26.sp)
            }
        }
        "text"->{
            val style=n.obj("textStyle");val token=c.document.getAsJsonObject("theme").getAsJsonObject("typography").getAsJsonObject(style?.str("typography")?:"body")
            val background=n.obj("appearance")?.str("fill")!=null
            val m=if(background)modifier.fillMaxWidth().readabilitySurface(n)else modifier.fillMaxWidth()
            val color=if(background)PicoTheme.colorScheme.labelPrimaryLight else if(style?.str("color")=="seaInk")androidx.compose.ui.graphics.Color(0xFFE4F6F2) else PicoTheme.colorScheme.labelPrimary
            Text(c.text(n,read),modifier=m,color=color,fontSize=token.number("sizeDp",20f).sp,lineHeight=token.number("lineHeightDp",28f).sp,fontWeight=FontWeight(token.number("weight",500f).toInt()))
        }
    }
}
