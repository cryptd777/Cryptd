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

class MainActivity : ComponentActivity() {

    private lateinit var pickIsoButton: Button
    private lateinit var startButton: Button
    private lateinit var ramInput: EditText
    private lateinit var cpuInput: EditText
    private lateinit var gfxSpinner: Spinner
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
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedUri = uri
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
        vncPortInput = findViewById(R.id.vncPortInput)
        kvmCheck = findViewById(R.id.kvmCheck)
        stopButton = findViewById(R.id.stopButton)
        createDiskButton = findViewById(R.id.createDiskButton)
        diskSizeInput = findViewById(R.id.diskSizeInput)
        viewLogsButton = findViewById(R.id.viewLogsButton)

        val gfxOptions = listOf("virtio", "std", "cirrus")
        gfxSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gfxOptions)
        ramInput.setText("1024")
        cpuInput.setText("2")
        vncPortInput.setText("5901")
        diskSizeInput.setText("8")
        lastDiskPath = VmFiles.loadLastDiskPath(this)

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
            val uri = selectedUri ?: return@setOnClickListener
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
            if (name.contains("x86") || name.contains("amd64") || name.contains("i386")) {
                Toast.makeText(this, "This app runs arm64 guests. Please pick an arm64/aarch64 ISO.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (name.endsWith(".qcow2") || name.endsWith(".img") || name.endsWith(".raw")) {
                // Accept disk images without ISO check
            }

            if (gfx != "virtio") {
                Toast.makeText(this, "Only virtio GPU is supported on arm64. Using virtio.", Toast.LENGTH_SHORT).show()
                gfx = "virtio"
            }

            setUiEnabled(false)
            Thread {
                val isoPath = VmFiles.copyToPrivateStorage(this, uri)
                if (isoPath == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to read selected file", Toast.LENGTH_SHORT).show()
                        setUiEnabled(true)
                    }
                    return@Thread
                }

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
                VmLogStore.append(this, "ISO: $isoPath\n")
                VmLogStore.append(this, "Disk: $diskPath\n")
                VmLogStore.append(this, "RAM: ${ramMb}MB CPU: $cpuCores GFX: $gfx VNC: $vncPort KVM: $useKvm\n")
                val rc = NativeBridge.startVm(
                    isoPath,
                    bundle.qemuPath,
                    bundle.libDir,
                    bundle.shareDir,
                    diskPath,
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
        vncPortInput.isEnabled = enabled
        kvmCheck.isEnabled = enabled
        stopButton.isEnabled = enabled
        createDiskButton.isEnabled = enabled
        diskSizeInput.isEnabled = enabled
        viewLogsButton.isEnabled = enabled
    }

    companion object
}
