package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudiobookRepository(private val db: AudiobookDatabase) {
    private val dao = db.dao

    val allBooksWithTracks: Flow<List<BookWithTracks>> = dao.getAllBooksWithTracksFlow()
    val globalSettings: Flow<GlobalSettings?> = dao.getGlobalSettingsFlow()

    fun getBookWithTracks(bookId: Long): Flow<BookWithTracks?> = dao.getBookWithTracksFlow(bookId)

    suspend fun updateBookSkipIntro(bookId: Long, seconds: Int) = withContext(Dispatchers.IO) {
        dao.updateBookSkipIntro(bookId, seconds)
    }

    suspend fun updateBookSkipOutro(bookId: Long, seconds: Int) = withContext(Dispatchers.IO) {
        dao.updateBookSkipOutro(bookId, seconds)
    }

    suspend fun updateGlobalSettings(settings: GlobalSettings) = withContext(Dispatchers.IO) {
        dao.insertGlobalSettings(settings)
    }

    suspend fun savePlaybackProgress(bookId: Long, trackIndex: Int, positionMs: Long) = withContext(Dispatchers.IO) {
        dao.updatePlaybackProgress(bookId, trackIndex, positionMs, System.currentTimeMillis())
    }

    suspend fun getBookById(bookId: Long): Book? = withContext(Dispatchers.IO) {
        dao.getBookById(bookId)
    }

    suspend fun deleteBook(bookId: Long) = withContext(Dispatchers.IO) {
        val book = dao.getBookById(bookId)
        if (book != null) {
            dao.deleteTracksForBook(bookId)
            dao.deleteBook(bookId)
            // Delete actual folders if we are in app internal dir
            if (book.folderPath.contains("demo_")) {
                val dir = File(book.folderPath)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
        }
    }

    /**
     * Scans the external directories and parses books
     */
    suspend fun scanLocalDirectories(context: Context): Int = withContext(Dispatchers.IO) {
        val rootDirs = mutableListOf<File>()
        
        // Scan standard shared music fold
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (musicDir.exists() && musicDir.isDirectory) rootDirs.add(musicDir)

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir.exists() && downloadDir.isDirectory) rootDirs.add(downloadDir)

        val appDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (appDir != null && appDir.exists()) rootDirs.add(appDir)

        var importedCount = 0

        for (rootDir in rootDirs) {
            val subdirs = rootDir.listFiles { file -> file.isDirectory } ?: continue
            for (subdir in subdirs) {
                // Check if directory contains playables
                val audioFiles = subdir.listFiles { file -> 
                    file.isFile && (file.name.endsWith(".mp3", true) || file.name.endsWith(".wav", true) || file.name.endsWith(".m4a", true))
                } ?: continue

                if (audioFiles.isEmpty()) continue

                // Check if book already exists
                val existingBook = dao.getBookByFolderPath(subdir.absolutePath)
                if (existingBook != null) continue

                // Sort audio files naturally/alphabetically
                val sortedFiles = audioFiles.sortedWith { f1, f2 ->
                    naturalCompare(f1.name, f2.name)
                }

                val bookTitle = subdir.name
                val bookId = dao.insertBook(
                    Book(
                        title = bookTitle,
                        folderPath = subdir.absolutePath,
                        coverColorHex = getColorForTitle(bookTitle),
                        lastPlayedTimestamp = System.currentTimeMillis()
                    )
                )

                val tracks = sortedFiles.mapIndexed { index, file ->
                    Track(
                        bookId = bookId,
                        title = file.nameWithoutExtension,
                        filePath = file.absolutePath,
                        durationMs = estimateDuration(file),
                        trackNumber = index + 1
                    )
                }
                
                dao.insertTracks(tracks)
                importedCount++
            }
        }

        importedCount
    }

    /**
     * Seeds the app with beautiful and authentic test books containing synthesized audible WAV audio files.
     */
    suspend fun seedDemoBooks(context: Context) = withContext(Dispatchers.IO) {
        val appMusicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return@withContext
        if (!appMusicDir.exists()) appMusicDir.mkdirs()

        // Insure global settings exist
        if (dao.getGlobalSettingsSync() == null) {
            dao.insertGlobalSettings(GlobalSettings(id = 1, defaultSkipIntroSeconds = 15, defaultSkipOutroSeconds = 10))
        }

        // 1. Create Book 1
        val book1Dir = File(appMusicDir, "demo_Three_Body_Problem")
        if (!book1Dir.exists()) book1Dir.mkdirs()

        val book1Title = "三体（有声书精编版）"
        val existingBook1 = dao.getBookByFolderPath(book1Dir.absolutePath)
        if (existingBook1 == null) {
            // Write synthetic playable audio files and estimate/approximate correct metadata
            val file1 = File(book1Dir, "Chapter 01 科学边界.wav")
            generateSampleWav(file1, 60, 440f) // 60 seconds, 440Hz Sine Tone (Middle A)
            val file2 = File(book1Dir, "Chapter 02 射手与农夫.wav")
            generateSampleWav(file2, 45, 480f) // 45 seconds, slightly higher pitch
            val file3 = File(book1Dir, "Chapter 03 红岸基地.wav")
            generateSampleWav(file3, 90, 520f) // 90 seconds, much higher pitch

            val bookId = dao.insertBook(
                Book(
                    title = book1Title,
                    folderPath = book1Dir.absolutePath,
                    coverColorHex = "#FF1F77B4", // Deep blue
                    skipIntroSeconds = 10,  // custom skip intro 10s
                    skipOutroSeconds = 5,   // custom skip outro 5s
                    lastPlayedTimestamp = System.currentTimeMillis()
                )
            )

            dao.insertTracks(listOf(
                Track(bookId = bookId, title = "第1章 科学边界", filePath = file1.absolutePath, durationMs = 60000L, trackNumber = 1),
                Track(bookId = bookId, title = "第2章 射手与农夫", filePath = file2.absolutePath, durationMs = 45000L, trackNumber = 2),
                Track(bookId = bookId, title = "第3章 红岸基地", filePath = file3.absolutePath, durationMs = 90000L, trackNumber = 3)
            ))
        }

        // 2. Create Book 2
        val book2Dir = File(appMusicDir, "demo_Journey_to_the_West")
        if (!book2Dir.exists()) book2Dir.mkdirs()

        val book2Title = "西游记（热播评书）"
        val existingBook2 = dao.getBookByFolderPath(book2Dir.absolutePath)
        if (existingBook2 == null) {
            val file1 = File(book2Dir, "Episode 01 猴王出世.wav")
            generateSampleWav(file1, 35, 300f) // 35 seconds, 300Hz low tone
            val file2 = File(book2Dir, "Episode 02 大闹天宫.wav")
            generateSampleWav(file2, 50, 350f) // 50 seconds

            val bookId = dao.insertBook(
                Book(
                    title = book2Title,
                    folderPath = book2Dir.absolutePath,
                    coverColorHex = "#FFE377C2", // Rose/Pink accent
                    skipIntroSeconds = -1, // Use Global (defaults to 15s)
                    skipOutroSeconds = -1, // Use Global (defaults to 10s)
                    lastPlayedTimestamp = System.currentTimeMillis() - 86400000L // Yesterday
                )
            )

            dao.insertTracks(listOf(
                Track(bookId = bookId, title = "第1回 猴王初问世", filePath = file1.absolutePath, durationMs = 35000L, trackNumber = 1),
                Track(bookId = bookId, title = "第2回 大闹天宫府", filePath = file2.absolutePath, durationMs = 50000L, trackNumber = 2)
            ))
        }
    }

    /**
     * Estimates audio file duration. Standard files query MediaMetadataRetriever,
     * but demo files are read explicitly or hardcoded.
     */
    private fun estimateDuration(file: File): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLong() ?: 180000L // 3 minutes default
        } catch (e: Exception) {
            180000L // default
        }
    }

    /**
     * Natural numeric comparator for filenames like "第10集" vs "第2集"
     */
    private fun naturalCompare(s1: String, s2: String): Int {
        val r = Regex("(\\d+)")
        val m1 = r.findAll(s1).toList()
        val m2 = r.findAll(s2).toList()
        if (m1.isNotEmpty() && m2.isNotEmpty()) {
            val n1 = m1.first().value.toInt()
            val n2 = m2.first().value.toInt()
            val numCompare = n1.compareTo(n2)
            if (numCompare != 0) return numCompare
        }
        return s1.compareTo(s2, ignoreCase = true)
    }

    private fun getColorForTitle(title: String): String {
        val colors = listOf("#FF6200EE", "#FF3700B3", "#FF03DAC5", "#FF018786", "#FFD81B60", "#FFE91E63", "#FF9C27B0", "#FF673AB7", "#FF3F51B5", "#FF2196F3")
        val index = title.hashCode().coerceAtLeast(0) % colors.size
        return colors[index]
    }

    /**
     * Generates a fully compliant PCM WAV file with an audibly beating sine wave tone
     * this guarantees ExoPlayer will recognize, load, play, and seek correctly.
     */
    private fun generateSampleWav(file: File, durationSeconds: Int, frequency: Float) {
        if (file.exists()) return
        try {
            val sampleRate = 8000
            val numChannels = 1
            val bitsPerSample = 16
            val totalAudioLen = sampleRate * durationSeconds * numChannels * (bitsPerSample / 8)
            val totalDataLen = totalAudioLen + 36

            val header = ByteArray(44)
            // RIFF chunk descriptor
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            
            // Subchunk size
            val sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen)
            System.arraycopy(sizeBuf.array(), 0, header, 4, 4)
            
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            
            // "fmt " subchunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            
            // Format subchunk size: 16 (for PCM)
            val subSizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(16)
            System.arraycopy(subSizeBuf.array(), 0, header, 16, 4)
            
            // Audio format: 1 (PCM)
            val formatBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1.toShort())
            System.arraycopy(formatBuf.array(), 0, header, 20, 2)
            
            // Channels: 1
            val chanBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(numChannels.toShort())
            System.arraycopy(chanBuf.array(), 0, header, 22, 2)
            
            // Sample rate
            val rateBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate)
            System.arraycopy(rateBuf.array(), 0, header, 24, 4)
            
            // Byte rate (sampleRate * channels * bits/8)
            val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
            val byteRateBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate)
            System.arraycopy(byteRateBuf.array(), 0, header, 28, 4)
            
            // Block align
            val blockAlign = numChannels * (bitsPerSample / 8)
            val alignBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(blockAlign.toShort())
            System.arraycopy(alignBuf.array(), 0, header, 32, 2)
            
            // Bits per sample
            val bitsBuf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(bitsPerSample.toShort())
            System.arraycopy(bitsBuf.array(), 0, header, 34, 2)
            
            // "data" chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            
            // Data size
            val dataLenBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalAudioLen)
            System.arraycopy(dataLenBuf.array(), 0, header, 40, 4)

            FileOutputStream(file).use { out ->
                // Write header
                out.write(header)
                
                // Write sine wave sample data
                val bufferSize = 1024
                val bufferBytes = ByteBuffer.allocate(bufferSize * 2).order(ByteOrder.LITTLE_ENDIAN)
                val totalSamples = sampleRate * durationSeconds
                var sampleCount = 0

                while (sampleCount < totalSamples) {
                    bufferBytes.clear()
                    val batchSize = minOf(bufferSize, totalSamples - sampleCount)
                    for (i in 0 until batchSize) {
                        val angle = 2.0 * Math.PI * frequency * (sampleCount + i) / sampleRate
                        val rawSample = (Math.sin(angle) * 32767.0).toInt().coerceIn(-32768, 32767)
                        bufferBytes.putShort(rawSample.toShort())
                    }
                    out.write(bufferBytes.array(), 0, batchSize * 2)
                    sampleCount += batchSize
                }
            }
        } catch (e: Exception) {
            Log.e("AudiobookRepository", "Error seeding demo audio files", e)
        }
    }
}
