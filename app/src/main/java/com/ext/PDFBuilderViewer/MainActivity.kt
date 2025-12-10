package com.ext.PDFBuilderViewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ext.pdf_builder_viewer.PDFView

/**
 * Demo Activity for PDF Viewer Library
 * - Allows loading PDF files via system file picker
 * - Displays page number
 * - Supports next / previous page buttons
 */
class MainActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private lateinit var pageNumberText: TextView
    private lateinit var prevButton: ImageView
    private lateinit var nextButton: ImageView

    private val PICK_PDF_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Apply system bar insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        pdfView = findViewById(R.id.pdfView)
        pageNumberText = findViewById(R.id.pageNumberText)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)

        /**
         * Listen for page changes from PDFView
         */
        pdfView.setOnPageChangeListener(object : PDFView.OnPageChangeListener {
            override fun onPageChanged(currentPage: Int, totalPages: Int) {
                updatePageNumber(currentPage, totalPages)
                updateNavigationButtons()
            }
        })

        // Navigation buttons
        prevButton.setOnClickListener { pdfView.showPreviousPage() }
        nextButton.setOnClickListener { pdfView.showNextPage() }

        // Open PDF button
        findViewById<Button>(R.id.btnOpenPdf).setOnClickListener {
            openFilePicker()
        }
    }

    /**
     * Open system file picker to choose a PDF
     */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, PICK_PDF_REQUEST)
    }

    /**
     * Handle selected PDF from file picker
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_PDF_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                try {
                    // Keep URI permission so app can access file later
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }

                // Load PDF with swipe navigation enabled
                pdfView.fromUri(uri)
                    .enableSwipeNavigation(true)
                    .load()
            }
        }
    }

    /**
     * Update page number text
     */
    private fun updatePageNumber(currentPage: Int, totalPages: Int) {
        pageNumberText.text = "$currentPage / $totalPages"
    }

    /**
     * Update button states (enable/disable)
     */
    private fun updateNavigationButtons() {
        prevButton.isEnabled = pdfView.canGoPrevious()
        nextButton.isEnabled = pdfView.canGoNext()

        prevButton.alpha = if (prevButton.isEnabled) 1f else 0.5f
        nextButton.alpha = if (nextButton.isEnabled) 1f else 0.5f
    }
}
