package tw.edu.pu.o365st.s1141837.digitalink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tw.edu.pu.o365st.s1141837.digitalink.ui.theme.DigitalinkTheme
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalinkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Doodle(
                        modifier = Modifier.padding(innerPadding)
                    )

                }
            }
        }
    }
}

@Composable
fun Doodle(modifier: Modifier = Modifier) {
    var recognizedText by remember {
        mutableStateOf("請在畫布區手寫，再按「開始辨識」按鈕")
    }

    var path by remember {

        /*
        val p = Path()
        p.moveTo(50f, 100f)  // 移動到起始點 (50f, 100f)
        p.lineTo(200f, 500f) // 畫一條線到 (200f, 500f)
        p.lineTo(450f, 400f) // 畫一條線到 (450f, 400f)
        p.close()                    // 閉合路徑
        mutableStateOf(p)    // 最後一行回傳包有 p 的 MutableState

         */
        mutableStateOf(
            Path().apply {
                moveTo(100f, 100f)  // 移動到起始點 (50f, 100f)
                lineTo(100f, 500f)
                lineTo(400f, 500f)// 畫一條線到 (200f, 500f)
                lineTo(400f, 600f) // 畫一條線到 (450f, 400f)
                close()
            }
        )

    }

    // ML Kit 墨水與筆劃收集器 ----------
    // inkBuilder 負責收集「整頁的全部筆劃」（把多個單一筆劃組合在一起）。
    // strokeBuilder 負責記錄「單一筆劃」（手指從按下到抬起，抬起後就歸零）。

    var inkBuilder by remember {
        mutableStateOf(Ink.builder())
    }

    var strokeBuilder by remember {
        mutableStateOf<Ink.Stroke.Builder?>(null)
    }


    // 記憶 ML Kit 模型與 Recognizer 實體 (繁體中文 zh-Hant) ----------

    // 取得語言辨識碼
    val modelIdentifier = remember {
        try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hant")
        } catch (e: Exception) {
            null
        }
    }

    // 建立語言模型物件
    val model = remember(modelIdentifier) {
        modelIdentifier?.let { DigitalInkRecognitionModel.builder(it).build() }
    }

    // 建立手寫辨識引擎
    val recognizer = remember(model) {
        model?.let { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build()) }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier.size(300.dp)
                .border(2.dp, Color.Gray)
                .pointerInput(Unit) {
                    // 監聽拖曳手勢
                    detectDragGestures(
                        onDragStart = { offset -> /* 手指按下 */
                            //recognizedText = "手指拖曳"


                            val x = offset.x
                            val y = offset.y
                            val t = System.currentTimeMillis()

                            path.moveTo(x, y)
                            //  ML Kit Ink.Stroke.Builder 紀錄起點
                            strokeBuilder = Ink.Stroke.builder()
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))

                        },
                        onDrag = { change, dragAmount -> /* 手指移動 */
                            //recognizedText = "手指拖曳"
                            change.consume()
                            val x = change.position.x
                            val y = change.position.y
                            val t = System.currentTimeMillis()


                            path.lineTo(x, y)
                            val tempPath = Path()

                            tempPath.addPath(path)
                            path = tempPath
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))



                        },
                        onDragEnd = { /* 手指離開 */
                            //recognizedText = "手指彈開"
                            strokeBuilder?.build()?.let { stroke ->
                                inkBuilder.addStroke(stroke)

                            }
                            strokeBuilder = null
                        }
                    )
                }
        ) {
            drawPath(path, Color.Black, 1f, Stroke(10f))
        }
        Row {
            // 開始辨識
            Button(onClick = {
                val ink = inkBuilder.build()

                if (ink.strokes.isEmpty()) {
                    recognizedText = "請先書寫內容"
                    return@Button
                }

                recognizedText = "正在處理中..."

                if (model != null) {
                    RemoteModelManager.getInstance()
                        .download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            recognizer?.recognize(ink)
                                ?.addOnSuccessListener { result ->
                                    if (result.candidates.isNotEmpty()) {
                                        val topCandidate = result.candidates[0].text
                                        recognizedText = "辨識結果: $topCandidate"
                                    } else {
                                        recognizedText = "辨識結果: 無法辨識"
                                    }
                                }
                                ?.addOnFailureListener { recognizedText = "辨識失敗: ${it.message}" }

                        }
                        .addOnFailureListener { recognizedText = "模型下載失敗" }
                } else {
                    recognizedText = "模型建立失敗"
                }

            }) {
                Text("開始辨識")
            }
            Spacer(modifier = Modifier.width(16.dp))

            // 清除畫布
            Button(onClick = {
                path = Path()
                // 清除 ML Kit 累積的筆畫資料
                inkBuilder = Ink.builder()
                strokeBuilder = null
                recognizedText = "請在畫布區手寫，再按「開始辨識」按鈕"

            }){
                Text("清除")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(recognizedText)
    }
}