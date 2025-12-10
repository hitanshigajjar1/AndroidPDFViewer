package com.ext.pdf_builder_viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs

/**
 * Custom PDF Viewer View
 * - Supports loading from URI, File, Asset, and ByteArray
 * - Swipe navigation (enable/disable)
 * - Page change listener callback
 */
class PDFView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // PDF renderer components
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var fileDescriptor: ParcelFileDescriptor? = null

    private lateinit var imageView: ImageView

    // Gesture detector for swipe navigation
    private lateinit var gestureDetector: GestureDetector

    private var pdfFile: File? = null

    // Flag to enable/disable swipe gestures
    private var enableSwipeNavigation = true

    // Page change listener callback
    private var onPageChangeListener: OnPageChangeListener? = null

    init {
        // Inflate custom layout
        LayoutInflater.from(context).inflate(R.layout.pdf_view_layout, this, true)

        imageView = findViewById(R.id.pdfImageView)

        // Setup swipe detector
        setupGestureDetector()
    }

    /**
     * Setup swipe gesture detection for page navigation
     */
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (!enableSwipeNavigation) return false
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                // Only detect horizontal swipes
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) showPreviousPage()
                        else showNextPage()
                        return true
                    }
                }
                return false
            }
        })

        // Attach touch listener to the image view
        imageView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    /**
     * Enable or disable swipe navigation
     */
    fun enableSwipeNavigation(enable: Boolean): PDFView {
        enableSwipeNavigation = enable
        return this
    }

    /**
     * Load PDF from URI
     */
    fun fromUri(uri: Uri): PDFView {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("pdf_temp", ".pdf", context.cacheDir)
            tempFile.deleteOnExit()

            FileOutputStream(tempFile).use { output ->
                inputStream?.copyTo(output)
            }
            inputStream?.close()

            fromFile(tempFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return this
    }

    /**
     * Load PDF from a File
     */
    fun fromFile(file: File): PDFView {
        try {
            closePdf()
            pdfFile = file

            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)

            currentPageIndex = 0
            showPage(currentPageIndex)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return this
    }

    /**
     * Load PDF from assets folder
     */
    fun fromAsset(assetName: String): PDFView {
        try {
            val inputStream = context.assets.open(assetName)
            val tempFile = File.createTempFile("pdf_asset", ".pdf", context.cacheDir)
            tempFile.deleteOnExit()

            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            fromFile(tempFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return this
    }

    /**
     * Load PDF from byte array
     */
    fun fromBytes(bytes: ByteArray): PDFView {
        try {
            val tempFile = File.createTempFile("pdf_bytes", ".pdf", context.cacheDir)
            tempFile.deleteOnExit()

            FileOutputStream(tempFile).use { output ->
                output.write(bytes)
            }

            fromFile(tempFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return this
    }

    /**
     * Display a specific page by index
     */
    private fun showPage(index: Int) {
        if (pdfRenderer == null) return
        if (index < 0 || index >= pdfRenderer!!.pageCount) return

        currentPage?.close()
        currentPage = pdfRenderer!!.openPage(index)
        currentPageIndex = index

        // Create bitmap for PDF page
        val bitmap = Bitmap.createBitmap(
            currentPage!!.width,
            currentPage!!.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE) // background

        currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        imageView.setImageBitmap(bitmap)

        // Notify page change listener
        onPageChangeListener?.onPageChanged(currentPageIndex + 1, pdfRenderer!!.pageCount)
    }

    /**
     * Next / Previous navigation controls
     */
    fun showNextPage() {
        if (canGoNext()) showPage(currentPageIndex + 1)
    }

    fun showPreviousPage() {
        if (canGoPrevious()) showPage(currentPageIndex - 1)
    }

    /**
     * Helper functions for navigation states
     */
    fun canGoNext(): Boolean =
        pdfRenderer != null && currentPageIndex < pdfRenderer!!.pageCount - 1

    fun canGoPrevious(): Boolean =
        currentPageIndex > 0

    /**
     * Reload current page
     */
    fun load() {
        if (pdfRenderer != null) showPage(currentPageIndex)
    }

    /**
     * Jump to a specific page (1-based index)
     */
    fun setPageNumber(pageNumber: Int) {
        showPage(pageNumber - 1)
    }

    fun getCurrentPageNumber(): Int = currentPageIndex + 1

    fun getPageCount(): Int = pdfRenderer?.pageCount ?: 0

    /**
     * Set listener for page change events
     */
    fun setOnPageChangeListener(listener: OnPageChangeListener?): PDFView {
        this.onPageChangeListener = listener
        return this
    }

    /**
     * Clean-up all PDF resources
     */
    private fun closePdf() {
        currentPage?.close()
        pdfRenderer?.close()
        fileDescriptor?.close()
        currentPage = null
        pdfRenderer = null
        fileDescriptor = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        closePdf()
    }

    /**
     * Callback interface for page change listener
     */
    interface OnPageChangeListener {
        fun onPageChanged(currentPage: Int, totalPages: Int)
    }
}
