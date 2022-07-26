package com.khue.downloadandunzipfile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.khue.downloadandunzipfile.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.*
import java.net.MalformedURLException
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private val downloadURL = "https://metanode.co/zip/meta_world.zip"
    private val appDirectory = File("${Environment.getExternalStorageDirectory()}/DownloadAndUnzipFile/")

    private var _binding: ActivityMainBinding?= null
    private val binding get() = _binding!!

    private val downloadFileProgress = MutableStateFlow(0)
    private val unzipFileProgress = MutableStateFlow(0)

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        qrCodeScanner()
        downloadFile()
        openHTMlFile()

        updateDownloadProgress()
        unzipFileProgress()
    }

    private fun updateDownloadProgress() {
        lifecycleScope.launch {
            downloadFileProgress.collect {
                //println(it)
                if(it == 100) {
                    binding.tvDownloadProgressValue.text = "Download Completed"
                    unzip(File("$appDirectory/${getFileNameFromURL(downloadURL)}"), appDirectory.absolutePath)
                } else {
                    binding.tvDownloadProgressValue.text = "$it %"
                }
            }
        }
    }

    private fun unzipFileProgress() {
        lifecycleScope.launch {
            unzipFileProgress.collect {
                println(it)
                if(it == 100) {
                    binding.tvUnzipProgressValue.text = "Unzip Completed"
                } else {
                    binding.tvUnzipProgressValue.text = "$it %"
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun downloadFile() {
        binding.btnDownloadFile.setOnClickListener {
            if(allPermissionsGranted()) {
                println("All permissions granted")
                try {
                    if (!appDirectory.exists()) {
                        if (appDirectory.mkdirs()) {
                            println("make dir")
                            println(appDirectory.absolutePath)
                        } else {
                            println("dir not make")
                            println(appDirectory.absolutePath)
                        }
                    }

                    if (appDirectory.exists()) {
                        val fileName = getFileNameFromURL(downloadURL)
                        val pathFile = File("${appDirectory.path}/$fileName")
                        lifecycleScope.launch(Dispatchers.IO) {
                            download(downloadURL, pathFile.absolutePath) { progress, length ->
                                downloadFileProgress.value = ((progress*100)/length).toInt()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                requestAllPermission()
            }
        }
    }

    fun download(link: String, path: String, progress: ((Long, Long) -> Unit)? = null): Long {
        val url = URL(link)
        val connection = url.openConnection()
        connection.connect()
        val length = connection.contentLengthLong
        url.openStream().use { input ->
            FileOutputStream(File(path)).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead = input.read(buffer)
                var bytesCopied = 0L
                while (bytesRead >= 0) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    progress?.invoke(bytesCopied, length)
                    bytesRead = input.read(buffer)
                }
                return bytesCopied
            }
        }
    }

    @Throws(IOException::class)
    fun unzip(zipFilePath: File, destDirectory: String) {

        File(destDirectory).run {
            if (!exists()) {
                mkdirs()
            }
        }

        var totalSizeCompressed = 0L
        val totalSize = zipFilePath.length()
        ZipFile(zipFilePath).use { zip ->

            zip.entries().asSequence().forEach { entry ->

                totalSizeCompressed += entry.compressedSize
                unzipFileProgress.value = ((totalSizeCompressed*100)/totalSize).toInt()

                zip.getInputStream(entry).use { input ->


                    val filePath = destDirectory + File.separator + entry.name

                    if (!entry.isDirectory) {
                        // if the entry is a file, extracts it
                        extractFile(input, filePath)
                    } else {
                        // if the entry is a directory, make the directory
                        val dir = File(filePath)
                        dir.mkdir()
                    }

                }

            }
        }
    }

    @Throws(IOException::class)
    private fun extractFile(inputStream: InputStream, destFilePath: String) {
        val bos = BufferedOutputStream(FileOutputStream(destFilePath))
        val bytesIn = ByteArray(BUFFER_SIZE)
        var read: Int
        while (inputStream.read(bytesIn).also { read = it } != -1) {
            bos.write(bytesIn, 0, read)
        }
        unzipFileProgress.value = 100
        bos.close()
    }

    private val BUFFER_SIZE = 4096

    private fun openHTMlFile() {
        val webView = binding.webView
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.builtInZoomControls = true
        webSettings.allowFileAccess = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                return true
            }
        }

        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

        binding.btnOpenFile.setOnClickListener {
            binding.tvDownloadProgress.visibility = View.GONE
            binding.tvDownloadProgressValue.visibility = View.GONE
            binding.tvUnzipProgress.visibility = View.GONE
            binding.tvDownloadProgressValue.visibility = View.GONE
            binding.webView.visibility = View.VISIBLE
            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestReadFilePermission()
            } else {
                webView.loadUrl("$appDirectory/index.html")
            }
        }
    }

    private var getReadFilePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Read file permission granted!", Toast.LENGTH_LONG).show()
                binding.webView.loadUrl("$appDirectory/index.html")
            } else {
                Toast.makeText(this, "You need the Read file permission!", Toast.LENGTH_LONG).show()
            }
        }

    private fun requestReadFilePermission() {
        getReadFilePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun qrCodeScanner() {
        binding.btnQrcodeScanner.setOnClickListener {
            startActivity(Intent(this, QRCodeScannerActivity::class.java))
        }
    }

    private val REQUIRED_PERMISSIONS = Manifest.permission.WRITE_EXTERNAL_STORAGE

    private fun allPermissionsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager() &&
                ContextCompat.checkSelfPermission(
                    this, REQUIRED_PERMISSIONS
                ) == PackageManager.PERMISSION_GRANTED

        } else {
            ContextCompat.checkSelfPermission(
                this, REQUIRED_PERMISSIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private var getPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            println(isGranted)
        }


    @RequiresApi(Build.VERSION_CODES.R)
    private val getManageFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (Environment.isExternalStorageManager()) {
                // enter you code here
            }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestAllPermission() {
        val uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID)

        val intent =
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            println("request permission 13")
            getManageFile.launch(intent)
            getPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
        } else {
            getPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun getFileNameFromURL(url: String): String {
        try {
            val resource = URL(url)
            val host = resource.host
            if (host.isNotEmpty() && url.endsWith(host)) {
                // handle ...example.com
                return ""
            }
        } catch (e: MalformedURLException) {
            return ""
        }
        val startIndex = url.lastIndexOf('/') + 1
        val length = url.length

        // find end index for ?
        var lastQMPos = url.lastIndexOf('?')
        if (lastQMPos == -1) {
            lastQMPos = length
        }

        // find end index for #
        var lastHashPos = url.lastIndexOf('#')
        if (lastHashPos == -1) {
            lastHashPos = length
        }

        // calculate the end index
        val endIndex = Math.min(lastQMPos, lastHashPos)
        return url.substring(startIndex, endIndex)
    }
}