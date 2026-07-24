package com.brarchive.app

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

object BrArchive {
    const val MAGIC: Long = 0x267052A0B125277DL
    const val VERSION: Int = 1
    const val ENTRY_NAME_LEN_MAX: Int = 247
    const val HEADER_SIZE: Int = 16
    const val DESCRIPTOR_SIZE: Int = 256

    private data class DescInfo(val name: String, val offset: Int, val len: Int)

    fun encode(entries: Map<String, File>, outputFile: File, dedup: Boolean) {
        val entryCount = entries.size
        val descriptors = mutableListOf<DescInfo>()
        val filesToWrite = mutableListOf<File>()

        var currentOffset = 0
        val hashToOffset = mutableMapOf<String, Int>()

        for ((name, file) in entries) {
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            // 【修改点】：将超长文件名传入异常
            if (nameBytes.size > ENTRY_NAME_LEN_MAX) {
                throw EntryNameTooLongException(name, nameBytes.size)
            }

            val len = file.length().toInt()

            if (dedup) {
                val hash = calculateMD5(file)
                if (hashToOffset.containsKey(hash)) {
                    descriptors.add(DescInfo(name, hashToOffset[hash]!!, len))
                } else {
                    hashToOffset[hash] = currentOffset
                    descriptors.add(DescInfo(name, currentOffset, len))
                    filesToWrite.add(file)
                    currentOffset += len
                }
            } else {
                descriptors.add(DescInfo(name, currentOffset, len))
                filesToWrite.add(file)
                currentOffset += len
            }
        }

        outputFile.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
            
            fun writeIntLE(v: Int) {
                out.write(v and 0xFF)
                out.write((v ushr 8) and 0xFF)
                out.write((v ushr 16) and 0xFF)
                out.write((v ushr 24) and 0xFF)
            }
            fun writeLongLE(v: Long) {
                out.write((v and 0xFF).toInt())
                out.write(((v ushr 8) and 0xFF).toInt())
                out.write(((v ushr 16) and 0xFF).toInt())
                out.write(((v ushr 24) and 0xFF).toInt())
                out.write(((v ushr 32) and 0xFF).toInt())
                out.write(((v ushr 40) and 0xFF).toInt())
                out.write(((v ushr 48) and 0xFF).toInt())
                out.write(((v ushr 56) and 0xFF).toInt())
            }

            writeLongLE(MAGIC)
            writeIntLE(entryCount)
            writeIntLE(VERSION)

            for (desc in descriptors) {
                val nameBytes = desc.name.toByteArray(Charsets.UTF_8)
                out.write(nameBytes.size)
                out.write(nameBytes)
                val padding = ByteArray(ENTRY_NAME_LEN_MAX - nameBytes.size)
                out.write(padding) 
                writeIntLE(desc.offset)
                writeIntLE(desc.len)
            }

            val buffer = ByteArray(64 * 1024)
            for (file in filesToWrite) {
                FileInputStream(file).use { fis ->
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    fun decode(inputFile: File, outDir: File) {
        RandomAccessFile(inputFile, "r").use { raf ->
            val header = ByteArray(HEADER_SIZE)
            raf.readFully(header)
            val hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            val magic = hb.long
            if (magic != MAGIC) throw MagicMismatchException(magic)
            val entryCount = hb.int
            val version = hb.int
            if (version != VERSION) throw UnsupportedVersionException(version)

            val descBytes = ByteArray(entryCount * DESCRIPTOR_SIZE)
            raf.readFully(descBytes)
            val db = ByteBuffer.wrap(descBytes).order(ByteOrder.LITTLE_ENDIAN)

            val descriptors = mutableListOf<DescInfo>()
            for (i in 0 until entryCount) {
                val nameLen = db.get().toInt() and 0xFF
                // 【修改点】：解析时若异常也报出长度
                if (nameLen > ENTRY_NAME_LEN_MAX) throw EntryNameTooLongException("<未知文件(正在解压)>", nameLen)
                
                val nameBytes = ByteArray(nameLen)
                db.get(nameBytes)
                db.position(db.position() + (ENTRY_NAME_LEN_MAX - nameLen))
                
                val offset = db.int
                val len = db.int
                descriptors.add(DescInfo(String(nameBytes, Charsets.UTF_8), offset, len))
            }

            val contentBase = HEADER_SIZE + (entryCount * DESCRIPTOR_SIZE).toLong()
            val buffer = ByteArray(64 * 1024)

            for (desc in descriptors) {
                val destFile = File(outDir, desc.name)
                destFile.parentFile?.mkdirs()

                raf.seek(contentBase + desc.offset)
                var bytesRemaining = desc.len
                
                FileOutputStream(destFile).use { fos ->
                    while (bytesRemaining > 0) {
                        val toRead = minOf(buffer.size, bytesRemaining)
                        val read = raf.read(buffer, 0, toRead)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        bytesRemaining -= read
                    }
                }
            }
        }
    }

    fun list(inputFile: File): List<String> {
        val names = mutableListOf<String>()
        RandomAccessFile(inputFile, "r").use { raf ->
            val header = ByteArray(HEADER_SIZE)
            if (raf.read(header) < HEADER_SIZE) return emptyList()
            val hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            if (hb.long != MAGIC) return emptyList()
            val entryCount = hb.int
            
            val descBytes = ByteArray(entryCount * DESCRIPTOR_SIZE)
            if (raf.read(descBytes) < descBytes.size) return emptyList()
            val db = ByteBuffer.wrap(descBytes).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until entryCount) {
                val nameLen = db.get().toInt() and 0xFF
                val nameBytes = ByteArray(nameLen)
                db.get(nameBytes)
                names.add(String(nameBytes, Charsets.UTF_8))
                db.position(db.position() + (ENTRY_NAME_LEN_MAX - nameLen) + 8)
            }
        }
        return names
    }

    private fun calculateMD5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}