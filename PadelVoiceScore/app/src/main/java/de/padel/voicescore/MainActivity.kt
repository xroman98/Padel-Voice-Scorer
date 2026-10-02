package de.padel.voicescore

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class MatchState(val pr:Int=0,val pb:Int=0,val gr:Int=0,val gb:Int=0,val sr:Int=0,val sb:Int=0,val serveRed:Boolean=true)
data class HistoryRow(val time:String,val action:String,val red:String,val blue:String,val games:String,val sets:String)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
 private var speech:SpeechRecognizer?=null; private var listening=false; private var tts:TextToSpeech?=null
 private var onCommand:((String)->Unit)?=null; private var onVoiceState:((String)->Unit)?=null
 private val requestMic=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it) startVoice() else onVoiceState?.invoke("Mikrofon nicht erlaubt")}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);tts=TextToSpeech(this,this)
  setContent { PadelTheme { PadelApp(this, onToggleVoice={cmd,state->onCommand=cmd;onVoiceState=state;toggleVoice()}, onSpeak={speak(it)}) } }
 }
 private fun toggleVoice(){if(listening)stopVoice() else if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startVoice()else requestMic.launch(Manifest.permission.RECORD_AUDIO)}
 private fun startVoice(){if(!SpeechRecognizer.isRecognitionAvailable(this)){onVoiceState?.invoke("Spracherkennung nicht verfügbar");return};listening=true;onVoiceState?.invoke("🎤 Sprachsteuerung aktiv")
  if(speech==null){speech=SpeechRecognizer.createSpeechRecognizer(this);speech?.setRecognitionListener(object:RecognitionListener{
   override fun onResults(b:Bundle){b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let{onCommand?.invoke(it)};restart()}
   override fun onError(e:Int){if(listening)restart()};override fun onReadyForSpeech(b:Bundle?){};override fun onBeginningOfSpeech(){};override fun onRmsChanged(v:Float){};override fun onBufferReceived(b:ByteArray?){};override fun onEndOfSpeech(){};override fun onPartialResults(b:Bundle?){};override fun onEvent(t:Int,b:Bundle?){}
  })};listenOnce() }
 private fun listenOnce(){val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"de-DE");putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3)};runCatching{speech?.startListening(i)}.onFailure{restart()}}
 private fun restart(){window.decorView.postDelayed({if(listening)listenOnce()},700)}
 private fun stopVoice(){listening=false;speech?.cancel();onVoiceState?.invoke("Sprachsteuerung ist aus")}
 private fun speak(t:String){tts?.speak(t,TextToSpeech.QUEUE_FLUSH,null,"score")}
 override fun onInit(s:Int){if(s==TextToSpeech.SUCCESS)tts?.language=Locale.GERMAN}
 override fun onDestroy(){speech?.destroy();tts?.shutdown();super.onDestroy()}
}

@Composable fun PadelTheme(content:@Composable()->Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF22C55E),background=Color(0xFF07111F),surface=Color(0xFF111C2D)),content=content)}

@Composable fun PadelApp(context:Context,onToggleVoice:(((String)->Unit),(String)->Unit)->Unit,onSpeak:(String)->Unit){
 val prefs=remember{context.getSharedPreferences("padel",Context.MODE_PRIVATE)}
 var state by remember{mutableStateOf(loadState(prefs))};var history by remember{mutableStateOf(loadHistory(prefs))};var undo by remember{mutableStateOf(listOf<MatchState>())};var voiceState by remember{mutableStateOf("Sprachsteuerung ist aus")};var fullscreen by remember{mutableStateOf(false)};var help by remember{mutableStateOf(false)}
 fun persist(){prefs.edit().putString("state",JSONObject().apply{put("pr",state.pr);put("pb",state.pb);put("gr",state.gr);put("gb",state.gb);put("sr",state.sr);put("sb",state.sb);put("serveRed",state.serveRed)}.toString()).putString("history",JSONArray(history.map{JSONObject().apply{put("time",it.time);put("action",it.action);put("red",it.red);put("blue",it.blue);put("games",it.games);put("sets",it.sets)}}).toString()).apply()}
 fun add(action:String){val row=HistoryRow(SimpleDateFormat("HH:mm:ss",Locale.GERMANY).format(Date()),action,label(state.pr,state.pb),label(state.pb,state.pr),"${state.gr}:${state.gb}","${state.sr}:${state.sb}");history=(listOf(row)+history).take(150);persist()}
 fun point(red:Boolean){undo=undo+state;state=if(red)state.copy(pr=state.pr+1,serveRed=!state.serveRed)else state.copy(pb=state.pb+1,serveRed=!state.serveRed);state=evaluate(state);add(if(red)"Punkt Rot" else "Punkt Blau");persist()}
 fun command(raw:String){val c=raw.lowercase(Locale.GERMAN);voiceState="Erkannt: $raw";when{("punkt rot" in c||c.trim()=="rot")->point(true);("punkt blau" in c||c.trim()=="blau")->point(false);("zurück" in c||"rückgängig" in c)->if(undo.isNotEmpty()){state=undo.last();undo=undo.dropLast(1);add("Rückgängig")};("aufschlag" in c||"service" in c)&&"rot" in c->{state=state.copy(serveRed=true);persist()};("aufschlag" in c||"service" in c)&&"blau" in c->{state=state.copy(serveRed=false);persist()};"spielstand" in c->onSpeak("Punkte Rot ${label(state.pr,state.pb)} zu Blau ${label(state.pb,state.pr)}. Spiele ${state.gr} zu ${state.gb}. Sätze ${state.sr} zu ${state.sb}")}}
 if(fullscreen) Scoreboard(state,onExit={fullscreen=false}) else MainScreen(state,history,voiceState,onRed={point(true)},onBlue={point(false)},onUndo={if(undo.isNotEmpty()){state=undo.last();undo=undo.dropLast(1);add("Rückgängig")}},onReset={state=MatchState();history=emptyList();undo=emptyList();persist()},onServe={state=state.copy(serveRed=it);persist()},onVoice={onToggleVoice(::command){voiceState=it}},onFullscreen={fullscreen=true},onHelp={help=true})
 if(help) AlertDialog(onDismissRequest={help=false},confirmButton={TextButton(onClick={help=false}){Text("Schließen")}},title={Text("Sprachbefehle")},text={Text("Punkt Rot / Rot\nPunkt Blau / Blau\nZurück / Rückgängig\nSpielstand\nAufschlag Rot / Service Rot\nAufschlag Blau / Service Blau")})
}

fun evaluate(x:MatchState):MatchState{var s=x;if((s.pr>=4||s.pb>=4)&&abs(s.pr-s.pb)>=2){s=if(s.pr>s.pb)s.copy(pr=0,pb=0,gr=s.gr+1)else s.copy(pr=0,pb=0,gb=s.gb+1);if((s.gr>=6||s.gb>=6)&&abs(s.gr-s.gb)>=2)s=if(s.gr>s.gb)s.copy(gr=0,gb=0,sr=s.sr+1)else s.copy(gr=0,gb=0,sb=s.sb+1)};return s}
fun label(p:Int,o:Int)=if(p>=3&&o>=3)when{p==o->"40";p==o+1->"Vorteil";else->"40"}else listOf("0","15","30","40")[p.coerceAtMost(3)]

@Composable fun MainScreen(s:MatchState,h:List<HistoryRow>,vs:String,onRed:()->Unit,onBlue:()->Unit,onUndo:()->Unit,onReset:()->Unit,onServe:(Boolean)->Unit,onVoice:()->Unit,onFullscreen:()->Unit,onHelp:()->Unit){val landscape=LocalConfiguration.current.screenWidthDp>LocalConfiguration.current.screenHeightDp;Column(Modifier.fillMaxSize().padding(if(landscape)8.dp else 12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("🎾 Padel Voice Score",fontSize=if(landscape)24.sp else 30.sp,fontWeight=FontWeight.Bold,color=Color(0xFF22C55E));Text(vs);TeamRow(s);Text("Sätze ${s.sr} : ${s.sb}",fontSize=28.sp);Text("Spiele ${s.gr} : ${s.gb}",fontSize=28.sp);ScoreRow(s,if(landscape)76 else 88);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onRed,Modifier.weight(1f),colors=ButtonDefaults.buttonColors(Color(0xFFE53935))){Text("+ Punkt Rot")};Button(onBlue,Modifier.weight(1f),colors=ButtonDefaults.buttonColors(Color(0xFF1976D2))){Text("+ Punkt Blau")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({onServe(true)},Modifier.weight(1f)){Text("Aufschlag Rot")};Button({onServe(false)},Modifier.weight(1f)){Text("Aufschlag Blau")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onVoice){Text("🎤 Sprache")};TextButton(onUndo){Text("↩ Zurück")};TextButton(onFullscreen){Text("📺 Groß")};TextButton(onHelp){Text("❓ Hilfe")};TextButton(onReset){Text("Neu")}};History(h,Modifier.weight(1f))}}
@Composable fun TeamRow(s:MatchState){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){TeamBox("TEAM ROT",Color(0xFFE53935),s.serveRed,Modifier.weight(1f));TeamBox("TEAM BLAU",Color(0xFF1976D2),!s.serveRed,Modifier.weight(1f))}}
@Composable fun TeamBox(t:String,c:Color,serve:Boolean,m:Modifier){Box(m.background(c,RoundedCornerShape(14.dp)).padding(12.dp),contentAlignment=Alignment.Center){Text(t+(if(serve)"  ●" else ""),fontWeight=FontWeight.Bold,fontSize=22.sp)}}
@Composable fun ScoreRow(s:MatchState,size:Int){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)){Text(label(s.pr,s.pb),fontSize=size.sp,fontWeight=FontWeight.Black,color=Color(0xFFE53935));Text(":",fontSize=(size*.7).sp);Text(label(s.pb,s.pr),fontSize=size.sp,fontWeight=FontWeight.Black,color=Color(0xFF1976D2))}}
@Composable fun History(h:List<HistoryRow>,m:Modifier){Card(m.fillMaxWidth()){LazyColumn(Modifier.padding(8.dp)){item{Row{listOf("Zeit","Aktion","Rot","Blau","Spiele","Sätze").forEach{Text(it,Modifier.weight(1f),fontWeight=FontWeight.Bold)}}};items(h){r->Row{listOf(r.time,r.action,r.red,r.blue,r.games,r.sets).forEach{Text(it,Modifier.weight(1f),fontSize=12.sp)}}}}}
@Composable fun Scoreboard(s:MatchState,onExit:()->Unit){Column(Modifier.fillMaxSize().background(Color(0xFF020617)).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally){TeamRow(s);Text("SÄTZE  ${s.sr} : ${s.sb}",fontSize=34.sp,fontWeight=FontWeight.Bold);Text("SPIELE  ${s.gr} : ${s.gb}",fontSize=34.sp,fontWeight=FontWeight.Bold);Box(Modifier.weight(1f),contentAlignment=Alignment.Center){ScoreRow(s,118)};TextButton(onExit){Text("Großanzeige schließen")}}}
fun loadState(p:android.content.SharedPreferences)=runCatching{JSONObject(p.getString("state","")!!).let{MatchState(it.optInt("pr"),it.optInt("pb"),it.optInt("gr"),it.optInt("gb"),it.optInt("sr"),it.optInt("sb"),it.optBoolean("serveRed",true))}}.getOrDefault(MatchState())
fun loadHistory(p:android.content.SharedPreferences)=runCatching{val a=JSONArray(p.getString("history","[]"));List(a.length()){i->a.getJSONObject(i).let{HistoryRow(it.optString("time"),it.optString("action"),it.optString("red"),it.optString("blue"),it.optString("games"),it.optString("sets"))}}}.getOrDefault(emptyList())
