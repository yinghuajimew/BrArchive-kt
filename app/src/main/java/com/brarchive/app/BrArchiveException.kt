package com.brarchive.app

sealed class BrArchiveException(message: String) : Exception(message)

class MagicMismatchException(val magic: Long) :
    BrArchiveException("文件头魔法值不匹配 (期待 2769805646197172093, 实际 $magic)")

class UnsupportedVersionException(val version: Int) :
    BrArchiveException("不支持的归档版本 $version")

// 【修改点】：在报错信息中追加具体的文件路径，帮助用户定位问题
class EntryNameTooLongException(val entryName: String, val length: Int) :
    BrArchiveException("内部路径过长！最大限度247字节，当前 $length 字节。\n请修改以下文件路径使其变短:\n$entryName")

class TooManyEntriesException(val count: Int) :
    BrArchiveException("包含条目过多: $count 超过了最大限制")

class ContentTooLargeException :
    BrArchiveException("内容块超出了 JVM 的最大允许范围")

class BrArchiveIoException(message: String, cause: Throwable? = null) :
    BrArchiveException(message)