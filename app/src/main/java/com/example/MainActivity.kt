package com.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.theme.MyApplicationTheme

import com.example.ui.WledManagerApp
import com.example.viewmodel.WledViewModel

class MainActivity : ComponentActivity() {

  // ViewModel scoped to the Activity — same instance passed into Compose below,
  // so dispatchKeyEvent and the UI share one source of truth.
  private val viewModel: WledViewModel by viewModels()

  // --- Nhận diện cử chỉ cho 1 nút A/B Shutter ---
  private val gestureHandler = Handler(Looper.getMainLooper())
  private var pressActive = false        // đã thấy ACTION_DOWN khởi đầu của phím đã gán
  private var longPressFired = false     // đã kích hoạt "giữ lâu" trong lần giữ này
  private var awaitingSecondTap = false  // đang chờ cú nhấn thứ 2 để thành "nhấn đúp"

  private val longPressRunnable = Runnable {
    if (pressActive && !longPressFired) {
      longPressFired = true
      awaitingSecondTap = false
      gestureHandler.removeCallbacks(singleTapRunnable)
      fireGesture(WledViewModel.RemoteGesture.LONG)
    }
  }

  private val singleTapRunnable = Runnable {
    awaitingSecondTap = false
    fireGesture(WledViewModel.RemoteGesture.SINGLE)
  }

  private companion object {
    const val LONG_PRESS_MS = 1200L
    const val DOUBLE_TAP_MS = 300L
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Keep screen always on during choreography/live control
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContent {
      MyApplicationTheme {
        WledManagerApp(viewModel = viewModel)
      }
    }
  }

  /**
   * Bắt phím từ nút bấm Bluetooth HID (A/B Shutter…).
   * - Chế độ "Học phím": ghi lại keyCode rồi nuốt sự kiện.
   * - Bình thường: phân biệt 1 nhấn / nhấn đúp / giữ lâu trên phím đã gán,
   *   nuốt sự kiện để không vô tình đổi âm lượng.
   */
  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val code = event.keyCode

    // 1) Chế độ học phím
    if (viewModel.btRemoteLearning.value) {
      if (event.action == KeyEvent.ACTION_DOWN && isLearnableKey(code)) {
        viewModel.onBtRemoteKeyLearned(code)
      }
      // Nuốt cả down/up của phím trong lúc học để tránh tác dụng phụ
      if (isLearnableKey(code)) return true
      return super.dispatchKeyEvent(event)
    }

    // 2) Kích hoạt theo cử chỉ
    val isMappedKey = viewModel.btRemoteEnabled.value &&
      code != KeyEvent.KEYCODE_UNKNOWN &&
      code == viewModel.btRemoteKeyCode.value

    if (!isMappedKey) return super.dispatchKeyEvent(event)

    when (event.action) {
      KeyEvent.ACTION_DOWN -> {
        if (event.repeatCount == 0) {
          pressActive = true
          longPressFired = false
          gestureHandler.removeCallbacks(longPressRunnable)
          gestureHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
        }
        return true
      }
      KeyEvent.ACTION_UP -> {
        gestureHandler.removeCallbacks(longPressRunnable)
        if (!pressActive) return true // up lạc (vd ngay sau khi học) → bỏ qua
        pressActive = false

        if (longPressFired) {
          longPressFired = false
          return true // đã xử lý ở giữ lâu
        }

        // Nhấn ngắn: phân biệt đơn / đúp
        if (awaitingSecondTap) {
          awaitingSecondTap = false
          gestureHandler.removeCallbacks(singleTapRunnable)
          fireGesture(WledViewModel.RemoteGesture.DOUBLE)
        } else {
          awaitingSecondTap = true
          gestureHandler.removeCallbacks(singleTapRunnable)
          gestureHandler.postDelayed(singleTapRunnable, DOUBLE_TAP_MS)
        }
        return true
      }
    }
    return true
  }

  private fun fireGesture(gesture: WledViewModel.RemoteGesture) {
    val action = viewModel.triggerRemoteGesture(gesture)
    Toast.makeText(this, "Nút Bluetooth: $action", Toast.LENGTH_SHORT).show()
  }

  /** Tránh nuốt các phím điều hướng hệ thống khi đang học phím. */
  private fun isLearnableKey(code: Int): Boolean {
    if (code == KeyEvent.KEYCODE_UNKNOWN) return false
    return code !in intArrayOf(
      KeyEvent.KEYCODE_BACK,
      KeyEvent.KEYCODE_HOME,
      KeyEvent.KEYCODE_APP_SWITCH,
      KeyEvent.KEYCODE_POWER
    )
  }

  override fun onDestroy() {
    gestureHandler.removeCallbacks(longPressRunnable)
    gestureHandler.removeCallbacks(singleTapRunnable)
    super.onDestroy()
  }
}
