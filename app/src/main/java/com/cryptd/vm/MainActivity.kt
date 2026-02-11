package com.cryptd.vm

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import android.content.Intent

class MainActivity : ComponentActivity() {

    private lateinit var pickIsoButton: Button
    private lateinit var startButton: Button
    private lateinit var ramInput: EditText
    private lateinit var cpuInput: EditText
    private lateinit var gfxSpinner: Spinner
    private lateinit var mediaSpinner: Spinner
    private lateinit var vncPortInput: EditText
    private lateinit var kvmCheck: CheckBox
    private lateinit var stopButton: Button
    private lateinit var createDiskButton: Button
    private lateinit var diskSizeInput: EditText
    private lateinit var viewLogsButton: Button
    private var lastDiskPath: String? = null

    private var selectedUri: Uri? = null

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist permission for later use
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Some providers only allow read permission; continue with read.
            }
            selectedUri = uri
            VmFiles.saveLastIsoUri(this, uri)
            pickIsoButton.text = DocumentFile.fromSingleUri(this, uri)?.name ?: "Selected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pickIsoButton = findViewById(R.id.pickIsoButton)
        startButton = findViewById(R.id.startButton)
        ramInput = findViewById(R.id.ramInput)
        cpuInput = findViewById(R.id.cpuInput)
        gfxSpinner = findViewById(R.id.gfxSpinner)
        mediaSpinner = findViewById(R.id.mediaSpinner)
        vncPortInput = findViewById(R.id.vncPortInput)
        kvmCheck = findViewById(R.id.kvmCheck)
        stopButton = findViewById(R.id.stopButton)
        createDiskButton = findViewById(R.id.createDiskButton)
        diskSizeInput = findViewById(R.id.diskSizeInput)
        viewLogsButton = findViewById(R.id.viewLogsButton)

        val gfxOptions = listOf("virtio", "virtio-device", "ramfb")
        gfxSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gfxOptions)
        val mediaOptions = listOf("Auto", "ISO (read-only)", "Disk image (read/write)")
        mediaSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mediaOptions)
        ramInput.setText("1024")
        cpuInput.setText("2")
        vncPortInput.setText("5901")
        diskSizeInput.setText("8")
        lastDiskPath = VmFiles.loadLastDiskPath(this)
        selectedUri = VmFiles.loadLastIsoUri(this)
        if (selectedUri != null) {
            val testFd = VmFiles.openDocumentFd(this, selectedUri!!, "r")
            if (testFd != null) {
                VmFiles.closeFd(testFd)
                pickIsoButton.text = DocumentFile.fromSingleUri(this, selectedUri!!)?.name ?: "Selected"
            } else {
                VmFiles.clearLastIsoUri(this)
                selectedUri = null
                pickIsoButton.text = "Pick ISO/QCOW2"
            }
        }

        pickIsoButton.setOnClickListener {
            pickFileLauncher.launch(arrayOf("application/x-iso9660-image", "application/octet-stream"))
        }

        stopButton.setOnClickListener {
            NativeBridge.stopVm()
            Toast.makeText(this, "VM stopped", Toast.LENGTH_SHORT).show()
        }

        createDiskButton.setOnClickListener {
            val sizeGb = diskSizeInput.text.toString().toIntOrNull() ?: 0
            if (sizeGb <= 0) {
                Toast.makeText(this, "Enter disk size in GB", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                val path = DiskCreator.createRawDisk(this, sizeGb)
                runOnUiThread {
                    if (path == null) {
                        Toast.makeText(this, "Failed to create disk", Toast.LENGTH_SHORT).show()
                    } else {
                        lastDiskPath = path
                        VmFiles.saveLastDiskPath(this, path)
                        Toast.makeText(this, "Disk created: ${path.substringAfterLast('/')}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        viewLogsButton.setOnClickListener {
            startActivity(android.content.Intent(this, LogActivity::class.java))
        }

        startButton.setOnClickListener {
            val uri = selectedUri ?: run {
                Toast.makeText(this, "Select an ISO or disk image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ramMb = ramInput.text.toString().toIntOrNull() ?: 1024
            val cpuCores = cpuInput.text.toString().toIntOrNull() ?: 2
            var gfx = gfxSpinner.selectedItem.toString()
            val vncPort = vncPortInput.text.toString().toIntOrNull() ?: 5901
            val useKvm = kvmCheck.isChecked

            if (vncPort < 5900 || vncPort > 5999) {
                Toast.makeText(this, "VNC port must be between 5900 and 5999", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val name = DocumentFile.fromSingleUri(this, uri)?.name?.lowercase() ?: ""
            val mediaMode = mediaSpinner.selectedItem.toString()
            val isDiskImage = when (mediaMode) {
                "ISO (read-only)" -> false
                "Disk image (read/write)" -> true
                else -> name.endsWith(".qcow2") || name.endsWith(".img") || name.endsWith(".raw")
            }
            if (name.contains("x86") || name.contains("amd64") || name.contains("i386")) {
                Toast.makeText(this, "This app runs arm64 guests. Please pick an arm64/aarch64 ISO.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            setUiEnabled(false)
            Thread {
                val fdMode = if (isDiskImage) "rw" else "r"
                val fd = VmFiles.openDocumentFd(this, uri, fdMode)
                if (fd == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to open selected file. Please reselect.", Toast.LENGTH_SHORT).show()
                        setUiEnabled(true)
                    }
                    return@Thread
                }
                VmFiles.saveLastIsoUri(this, uri)

                val bundle = QemuInstaller.ensureQemuBundle(this)
                if (bundle == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Missing QEMU bundle in assets", Toast.LENGTH_SHORT).show()
                        setUiEnabled(true)
                    }
                    return@Thread
                }

                val logPath = VmLogStore.startSession(this).absolutePath
                var diskPath = lastDiskPath ?: ""
                if (diskPath.isNotEmpty()) {
                    val f = java.io.File(diskPath)
                    if (!f.exists()) {
                        diskPath = ""
                        lastDiskPath = null
                    }
                }

                var isoPath = ""
                var isoFd = -1
                var diskFd = -1
                if (isDiskImage) {
                    diskPath = "/proc/self/fd/$fd"
                    diskFd = fd
                } else {
                    isoPath = "/proc/self/fd/$fd"
                    isoFd = fd
                }

                VmLogStore.append(this, "ISO URI: $uri\n")
                VmLogStore.append(this, "ISO Path: $isoPath\n")
                VmLogStore.append(this, "Disk: $diskPath\n")
                VmLogStore.append(this, "RAM: ${ramMb}MB CPU: $cpuCores GFX: $gfx VNC: $vncPort KVM: $useKvm\n")
                val rc = NativeBridge.startVm(
                    isoPath,
                    isoFd,
                    bundle.qemuPath,
                    bundle.libDir,
                    bundle.shareDir,
                    diskPath,
                    diskFd,
                    logPath,
                    ramMb,
                    cpuCores,
                    gfx,
                    vncPort,
                    useKvm
                )

                runOnUiThread {
                    if (rc == 0) {
                        startActivity(
                            android.content.Intent(this, VncActivity::class.java)
                                .putExtra(VncActivity.EXTRA_HOST, "127.0.0.1")
                                .putExtra(VncActivity.EXTRA_PORT, vncPort)
                        )
                    } else {
                        Toast.makeText(this, "Failed to start VM (code $rc). Check logs.", Toast.LENGTH_LONG).show()
                    }
                    setUiEnabled(true)
                }
            }.start()
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        pickIsoButton.isEnabled = enabled
        startButton.isEnabled = enabled
        ramInput.isEnabled = enabled
        cpuInput.isEnabled = enabled
        gfxSpinner.isEnabled = enabled
        mediaSpinner.isEnabled = enabled
        vncPortInput.isEnabled = enabled
        kvmCheck.isEnabled = enabled
        stopButton.isEnabled = enabled
        createDiskButton.isEnabled = enabled
        diskSizeInput.isEnabled = enabled
        viewLogsButton.isEnabled = enabled
    }

    companion object
}
