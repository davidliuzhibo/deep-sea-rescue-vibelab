package com.vrgames.deeprescue.spatial

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.vrgames.deeprescue.domain.model.AudioSettings

/** Original quiet score and distinguishable feedback. Default quiet, stopped on pause/background. */
class RescueAudio(context:Context) {
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool=SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
    private val ids=mutableMapOf<String,Int>();private val loaded=mutableSetOf<Int>();private val streams=mutableListOf<Int>()
    private var music:MediaPlayer?=null
    private var settings=AudioSettings();private var active=false;private var prepared=false;private var closed=false
    init {
        pool.setOnLoadCompleteListener { _,id,status->if(status==0)loaded+=id else Log.w("DeepSeaRescue","SFX load failed id=$id status=$status") }
        for(name in listOf("select","inspect","clamp","cut","air","warning","progress"))try {
            context.assets.openFd("audio/$name.wav").use{ids[name]=pool.load(it,1)}
        }catch(e:Exception){Log.w("DeepSeaRescue","SFX unavailable: $name",e)}
        try {
            val player=MediaPlayer();music=player;player.setAudioAttributes(attributes);player.isLooping=true;player.setVolume(.08f,.08f)
            context.assets.openFd("audio/marine-ambience.wav").use{player.setDataSource(it.fileDescriptor,it.startOffset,it.length)}
            player.setOnPreparedListener{prepared=true;apply()};player.setOnErrorListener{_,what,extra->prepared=false;Log.w("DeepSeaRescue","Music error $what/$extra");true};player.prepareAsync()
        }catch(e:Exception){Log.w("DeepSeaRescue","Music unavailable",e)}
    }
    fun update(value:AudioSettings,foreground:Boolean) {settings=value;active=foreground;apply();if(!active||!settings.sfx){streams.forEach{pool.stop(it)};streams.clear()}}
    private fun apply(){if(closed||!prepared)return;runCatching{if(active&&settings.music){if(music?.isPlaying!=true)music?.start()}else if(music?.isPlaying==true)music?.pause()}}
    fun effect(name:String) {if(closed||!active||!settings.sfx)return;val id=ids[name]?:return;if(id !in loaded)return;val stream=pool.play(id,.18f,.18f,1,0,1f);if(stream!=0){streams+=stream;while(streams.size>16)streams.removeAt(0)}}
    fun close(){if(closed)return;closed=true;active=false;streams.forEach{pool.stop(it)};pool.release();music?.release();music=null}
}
