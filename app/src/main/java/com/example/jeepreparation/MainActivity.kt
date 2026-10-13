package com.example.jeepreparation
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
 data class Question(val id:Int, var text:String, var options:List<String> = listOf("A","B","C","D"), var answer:Int = -1, var chosen:Int = -1)
 private val questions= mutableListOf<Question>()
 private val selected= linkedSetOf<Int>()
 private var nextId=1
 private var current=0
 private var testQuestions=listOf<Question>()
 private lateinit var content:LinearLayout
 private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
 private val pdfPicker=registerForActivityResult(ActivityResultContracts.GetContent()){ if(it!=null) readPdf(it) }
 private val imagePicker=registerForActivityResult(ActivityResultContracts.GetContent()){ uri -> if(uri!=null) try { detectText(MediaStore.Images.Media.getBitmap(contentResolver,uri)) } catch(e:Exception){ toast("Could not open image") } }
 private val camera=registerForActivityResult(ActivityResultContracts.TakePicturePreview()){ if(it!=null) detectText(it) else toast("No photo captured") }
 override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); showHome() }
 private fun setup(title:String){
  val root=LinearLayout(this).apply{orientation=1;setBackgroundColor(0xFFF5F7FB.toInt())}
  root.addView(TextView(this).apply{text="  JEE PREP  ·  "+title;textSize=21f;setTextColor(-1);setBackgroundColor(0xFF173B70.toInt());setPadding(18,20,12,20);gravity=Gravity.CENTER_VERTICAL})
  val scroll=ScrollView(this);content=LinearLayout(this).apply{orientation=1;setPadding(16,14,16,24)};scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
 }
 private fun label(s:String,size:Float=16f){content.addView(TextView(this).apply{text=s;textSize=size;setTextColor(0xFF1C2D45.toInt());setPadding(2,10,2,8)})}
 private fun button(s:String,action:()->Unit){val b=Button(this).apply{text=s;isAllCaps=false;textSize=15f;setTextColor(-1);backgroundTintList=android.content.res.ColorStateList.valueOf(0xFF2459A6.toInt())};content.addView(b,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=7});b.setOnClickListener{action()}}
 private fun field(hint:String,multi:Boolean=false):EditText{val e=EditText(this).apply{this.hint=hint;textSize=16f;setPadding(12,10,12,10);setBackgroundColor(-1)};if(multi){e.minLines=3;e.gravity=Gravity.TOP;e.inputType=android.text.InputType.TYPE_CLASS_TEXT.toInt() or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE.toInt()};content.addView(e,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=8});return e}
 private fun showHome(){setup("HOME");label("Your personal JEE practice space",22f);label("Create your own tests. Import PDFs and photos, review detected questions, and practise.");button("Create / select questions"){showBank()};button("Import question-paper PDF"){pdfPicker.launch("application/pdf")};button("Scan question with camera"){camera.launch(null)};button("Choose question photo"){imagePicker.launch("image/*")};button("Start test from selected questions"){startTest()};button("Ask ChatGPT inside this app"){showChat()};button("How it works"){AlertDialog.Builder(this).setTitle("JEE Prep").setMessage("Review OCR carefully: maths symbols and scanned text can be misread. Select questions and set answer keys for automatic scoring.").setPositiveButton("OK",null).show()}}
 private fun showBank(){
  setup("QUESTION BANK");label("Add a question",19f);val qf=field("Question text…",true);val opts=(0..3).map{field("Option "+('A'.code+it).toChar())};val key=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Answer key not set","Correct answer: A","Correct answer: B","Correct answer: C","Correct answer: D"))};content.addView(key)
  button("Save question"){val t=qf.text.toString().trim();if(t.isBlank()){toast("Enter question text");return@button};questions.add(Question(nextId++,t,opts.mapIndexed{i,e->e.text.toString().trim().ifBlank{"Option "+('A'.code+i).toChar()}},if(key.selectedItemPosition==0)-1 else key.selectedItemPosition-1));toast("Question saved");showBank()}
  label("Saved questions: "+questions.size,19f)
  questions.forEach{q->val box=LinearLayout(this).apply{orientation=1;setPadding(10,8,10,8);setBackgroundColor(-1)};val cb=CheckBox(this).apply{text="Q"+q.id+". "+q.text;isChecked=selected.contains(q.id);textSize=15f};cb.setOnCheckedChangeListener{_,checked->if(checked)selected.add(q.id)else selected.remove(q.id)};box.addView(cb);box.addView(TextView(this).apply{text=q.options.mapIndexed{i,s->""+('A'.code+i).toChar()+". "+s}.joinToString("\n");textSize=13f;setTextColor(0xFF52627A.toInt())});box.addView(Button(this).apply{text="Delete";isAllCaps=false;setOnClickListener{questions.remove(q);selected.remove(q.id);showBank()}});content.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=8})}
  button("Start test ("+selected.size+" selected)"){startTest()};button("Home"){showHome()}
 }
 private fun readPdf(uri:Uri){toast("Reading PDF…");Thread{try{val f=File(cacheDir,"import.pdf");contentResolver.openInputStream(uri)!!.use{input->FileOutputStream(f).use{output->input.copyTo(output)}};val renderer=PdfRenderer(android.os.ParcelFileDescriptor.open(f,android.os.ParcelFileDescriptor.MODE_READ_ONLY));val bitmaps= mutableListOf<Bitmap>();for(i in 0 until minOf(renderer.pageCount,25)){val p=renderer.openPage(i);val scale=minOf(2.0,1400.0/p.width);val bmp=Bitmap.createBitmap((p.width*scale).toInt().coerceAtLeast(1),(p.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888);p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();bitmaps.add(bmp)};renderer.close();runOnUiThread{recognizePages(bitmaps,0,StringBuilder())}}catch(e:Exception){runOnUiThread{toast("Could not read this PDF")}}}.start()}
 private fun recognizePages(pages:List<Bitmap>,i:Int,out:StringBuilder){if(i>=pages.size){showDetected(out.toString());return};recognizer.process(InputImage.fromBitmap(pages[i],0)).addOnSuccessListener{out.append(it.text).append("\n\n");recognizePages(pages,i+1,out)}.addOnFailureListener{recognizePages(pages,i+1,out)}}
 private fun detectText(bitmap:Bitmap){toast("Detecting text…");recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener{showDetected(it.text)}.addOnFailureListener{toast("Detection failed. Try a clearer image.")}}
 private fun showDetected(text:String){setup("REVIEW OCR");label("Review detected text",20f);label("OCR can misread equations. Edit the text before saving.");val e=field("Detected text",true);e.setText(text);button("Split into questions"){val raw=e.text.toString().trim();val chunks=Pattern.compile("(?m)(?=^\\s*(?:Q(?:uestion)?\\s*)?\\d{1,3}[.)]\\s+)").split(raw).map{it.trim()}.filter{it.length>8};val parts=if(chunks.size>1)chunks else raw.split(Regex("\\n\\s*\\n")).map{it.trim()}.filter{it.length>8};if(parts.isEmpty()){toast("No questions detected; add manually");return@button};parts.forEach{questions.add(Question(nextId++,it.take(1800)))};toast("Added "+parts.size+" question(s); please review");showBank()};button("Save all text as one question"){val v=e.text.toString().trim();if(v.isNotBlank()){questions.add(Question(nextId++,v.take(1800)));showBank()}else toast("No text found")};button("Home"){showHome()}}
 private fun startTest(){testQuestions=questions.filter{selected.contains(it.id)};if(testQuestions.isEmpty()){toast("Select questions in question bank first");showBank();return};current=0;showTest()}
 private fun showTest(){setup("TEST "+(current+1)+"/"+testQuestions.size);val q=testQuestions[current];label("Question "+(current+1)+" of "+testQuestions.size,14f);label(q.text,19f);val group=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL};q.options.forEachIndexed{i,s->group.addView(RadioButton(this).apply{text=""+('A'.code+i).toChar()+". "+s;textSize=16f;id=100+i;isChecked=q.chosen==i})};content.addView(group);group.setOnCheckedChangeListener{_,id->q.chosen=id-100};button(if(current==testQuestions.lastIndex)"Submit test" else "Save & next"){if(current<testQuestions.lastIndex){current++;showTest()}else showResults()};button("Previous"){if(current>0){current--;showTest()}};button("Exit test"){showHome()}}
 private fun showResults(){setup("RESULTS");val graded=testQuestions.filter{it.answer>=0};val score=graded.count{it.chosen==it.answer};label("Test submitted",24f);label("Attempted: "+testQuestions.count{it.chosen>=0}+" / "+testQuestions.size);label("Correct (questions with answer keys): "+score+" / "+graded.size);label("Questions without answer keys cannot be auto-graded.");testQuestions.forEachIndexed{i,q->label((i+1).toString()+". "+q.text.take(150));label("Your answer: "+if(q.chosen<0)"Not answered" else ('A'.code+q.chosen).toChar()+" · Key: "+if(q.answer<0)"Not set" else ('A'.code+q.answer).toChar(),13f)};button("Question bank"){showBank()};button("Home"){showHome()}}
 private fun showChat(){setup("AI CHAT");label("ChatGPT",22f);label("Sign in if prompted. Chat availability depends on your account and internet connection.");val web=WebView(this);web.settings.javaScriptEnabled=true;web.settings.domStorageEnabled=true;web.webViewClient=WebViewClient();content.addView(web,LinearLayout.LayoutParams(-1,0,1f));web.loadUrl("https://chatgpt.com/");button("Back to JEE Prep"){web.stopLoading();showHome()}}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 override fun onDestroy(){super.onDestroy();recognizer.close()}
}
