package kafka.log;

import kafka.message.ByteBufferMessageSet;
import kafka.utils.Logging;
import kafka.utils.Time;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Optional;

/**
 * A segment of the log. Each segment has two a components log and an index. The log is a FileMessageSet containing
 * the actual messages. The index is an OffsetIndex that maps from logical offsets to physical file positions. Each
 * segment has a base offset which is an offset <= the least offset of any message in this segment and > any offset in
 * any previous segment.
 */
//@nonthreadsafe;
public class LogSegment extends Logging {
    public FileMessageSet log;
    public OffsetIndex index;
    public Long baseOffset;
    public Integer indexIntervalBytes;
    public Long rollJitterMs;
    public Time time;
    public Long created = time.milliseconds();
    /* the number of bytes since we last added an entry in the offset index */
    private Integer bytesSinceLastIndexEntry = 0;

    /**
     * A segment with a base offset of [base_offset] would be stored in two files, a [base_offset].index and a [base_offset].log file.
     *
     * @param log                The message set containing log entries
     * @param index              The offset index
     * @param baseOffset         A lower bound on the offsets in this segment
     * @param indexIntervalBytes The approximate number of bytes between entries in the index
     * @param time               The time instance
     */
    public LogSegment(FileMessageSet log, OffsetIndex index, Long baseOffset, Integer indexIntervalBytes, Long rollJitterMs, Time time) {
        this.log = log;
        this.index = index;
        this.baseOffset = baseOffset;
        this.indexIntervalBytes = indexIntervalBytes;
        this.rollJitterMs = rollJitterMs;
        this.time = time;
    }

    //
    public LogSegment(File dir, Long startOffset, Integer indexIntervalBytes, Integer maxIndexSize, Long rollJitterMs, Time time) throws FileNotFoundException {
        this(new FileMessageSet(Log.logFilename(dir, startOffset)),
                new OffsetIndex(Log.indexFilename(dir, startOffset), startOffset, maxIndexSize),
                startOffset,
                indexIntervalBytes,
                rollJitterMs,
                time);
    }

    //
  /* Return the size in bytes of this log segment */
    public Long size() {
        return log.sizeInBytes().longValue();
    }
//

    /**
     * Append the given messages starting with the given offset. Add
     * an entry to the index if needed.
     * <p>
     * It is assumed this method is being called from within a lock.
     *
     * @param offset   The first offset in the message set.
     * @param messages The messages to append.
     */
    //@nonthreadsafe;
    public void append(Long offset, ByteBufferMessageSet messages) {
        if (messages.sizeInBytes() > 0) {
            trace(String.format("Inserting %d bytes at offset %d at position %d", messages.sizeInBytes(), offset, log.sizeInBytes()));
            // append an entry to the index (if needed);
            if (bytesSinceLastIndexEntry > indexIntervalBytes) {
                index.append(offset, log.sizeInBytes());
                this.bytesSinceLastIndexEntry = 0;
            }
            // append the messages;
            log.append(messages);
            this.bytesSinceLastIndexEntry += messages.sizeInBytes();
        }
    }

    /**
     * Find the physical file position for the first message with offset >= the requested offset.
     * <p>
     * The lowerBound argument is an optimization that can be used if we already know a valid starting position
     * in the file higher than the greatest-lower-bound from the index.
     *
     * @param offset               The offset we want to translate
     * @param startingFilePosition A lower bound on the file position from which to begin the search. This is purely an optimization and
     *                             when omitted, the search will begin at the position in the offset index.
     * @return The position in the log storing the message with the least offset >= the requested offset or null if no message meets this criteria.
     */
    //@threadsafe;
    OffsetPosition translateOffset(Long offset, Integer startingFilePosition) {
        OffsetPosition mapping = index.lookup(offset);
        return log.searchFor(offset, Math.max(mapping.position, startingFilePosition));
    }
//
        /**
         * Read a message set from this segment beginning with the first offset >= startOffset. The message set will include
         * no more than maxSize bytes and will end before maxOffset if a maxOffset is specified.
         *
         * @param startOffset A lower bound on the first offset to include in the message set we read
         * @param maxSize The maximum number of bytes to include in the message set we read
         * @param maxOffset An optional maximum offset for the message set we read
         *
         * @return The fetched data and the offset metadata of the first message whose offset is >= startOffset,
         *         or null if the startOffset is larger than the largest offset in this log
         */

//        @threadsafe;
//        public FetchDataInfo  read(Long startOffset, Optional<Long> maxOffset, Integer maxSize) {
//        if(maxSize < 0)
//            throw new IllegalArgumentException("Invalid max size for log read (%d)".format(maxSize));
//
//        val logSize = log.sizeInBytes // this may change, need to save a consistent copy;
//        val startPosition = translateOffset(startOffset);
//
//        // if the start position is already off the end of the log, return null;
//        if(startPosition == null)
//            return null;
//
//        val offsetMetadata = new LogOffsetMetadata(startOffset, this.baseOffset, startPosition.position);
//
//        // if the size is zero, still return a log segment but with zero size;
//        if(maxSize == 0)
//            return FetchDataInfo(offsetMetadata, MessageSet.Empty);
//
//        // calculate the length of the message set to read based on whether or not they gave us a maxOffset;
//        val length =
//                maxOffset match {
//            case None =>
//                // no max offset, just use the max size they gave unmolested;
//                maxSize;
//            case Some(offset) => {
//                // there is a max offset, translate it to a file position and use that to calculate the max read size;
//                if(offset < startOffset)
//                    throw new IllegalArgumentException("Attempt to read with a maximum offset (%d) less than the start offset (%d).".format(offset, startOffset));
//                val mapping = translateOffset(offset, startPosition.position);
//                val endPosition =
//                if(mapping == null)
//                    logSize // the max offset is off the end of the log, use the end of the file;
//                else;
//                    mapping.position;
//                min(endPosition - startPosition.position, maxSize);
//            }
//        }
//        FetchDataInfo(offsetMetadata, log.read(startPosition.position, length));
//        }
//
//        /**
//         * Run recovery on the given segment. This will rebuild the index from the log file and lop off any invalid bytes from the end of the log and index.
//         *
//         * @param maxMessageSize A bound the memory allocation in the case of a corrupt message size--we will assume any message larger than this
//         * is corrupt.
//         *
//         * @return The number of bytes truncated from the log
//         */
//        @nonthreadsafe;
//        public Integer  recover(Int maxMessageSize) {
//                index.truncate();
//                index.resize(index.maxIndexSize);
//                var validBytes = 0;
//                var lastIndexEntry = 0;
//                val iter = log.iterator(maxMessageSize);
//        try {
//            while(iter.hasNext) {
//                val entry = iter.next;
//                entry.message.ensureValid();
//                if(validBytes - lastIndexEntry > indexIntervalBytes) {
//                    // we need to decompress the message, if required, to get the offset of the first uncompressed message;
//                    val startOffset =
//                            entry.message.compressionCodec match {
//                        case NoCompressionCodec =>
//                            entry.offset;
//                        case _ =>
//                            ByteBufferMessageSet.decompress(entry.message).head.offset;
//                    }
//                    index.append(startOffset, validBytes);
//                    lastIndexEntry = validBytes;
//                }
//                validBytes += MessageSet.entrySize(entry.message);
//            }
//        } catch {
//            case InvalidMessageException e =>
//                logger.warn("Found invalid messages in log segment %s at byte offset %d: %s.".format(log.file.getAbsolutePath, validBytes, e.getMessage));
//        }
//        val truncated = log.sizeInBytes - validBytes;
//        log.truncateTo(validBytes);
//        index.trimToValidSize();
//        truncated;
//        }
//
//        override def toString() = "LogSegment(baseOffset=" + baseOffset + ", size=" + size + ")";
//
//        /**
//         * Truncate off all index and log entries with offsets >= the given offset.
//         * If the given offset is larger than the largest message in this segment, do nothing.
//         * @param offset The offset to truncate to
//         * @return The number of log bytes truncated
//         */
//        // truncateTo(offset)就是把end到offset的起始位置;
//        @nonthreadsafe;
//        public Integer  truncateTo(Long offset) {
//                val mapping = translateOffset(offset);
//        if(mapping == null)
//            return 0;
//        index.truncateTo(offset);
//        // after truncation, reset and allocate more space for the (new currently  active) index;
//        index.resize(index.maxIndexSize);
//        val bytesTruncated = log.truncateTo(mapping.position);
//        if(log.sizeInBytes == 0)
//            created = time.milliseconds;
//        bytesSinceLastIndexEntry = 0;
//        bytesTruncated;
//        }
//
//        /**
//         * Calculate the offset that would be used for the next message to be append to this segment.
//         * Note that this is expensive.
//         */
//        @threadsafe;
//        public Long  nextOffset() {
//                val ms = read(index.lastOffset, None, log.sizeInBytes);
//        if(ms == null) {
//            baseOffset;
//        } else {
//            ms.messageSet.lastOption match {
//                case None => baseOffset;
//                case Some(last) => last.nextOffset;
//            }
//        }
//        }
//
//        /**
//         * Flush this log segment to disk
//         */
//        @threadsafe;
//        def flush() {
//            LogFlushStats.logFlushTimer.time {
//                log.flush();
//                index.flush();
//            }
//        }
//
//        /**
//         * Change the suffix for the index and log file for this log segment
//         */
//        def changeFileSuffixes(String oldSuffix, String newSuffix) {
//            val logRenamed = log.renameTo(new File(Utils.replaceSuffix(log.file.getPath, oldSuffix, newSuffix)));
//            if(!logRenamed)
//                throw new KafkaStorageException("Failed to change the log file suffix from %s to %s for log segment %d".format(oldSuffix, newSuffix, baseOffset));
//            val indexRenamed = index.renameTo(new File(Utils.replaceSuffix(index.file.getPath, oldSuffix, newSuffix)));
//            if(!indexRenamed)
//                throw new KafkaStorageException("Failed to change the index file suffix from %s to %s for log segment %d".format(oldSuffix, newSuffix, baseOffset));
//        }
//
//        /**
//         * Close this log segment
//         */
//        def close() {
//            Utils.swallow(index.close);
//            Utils.swallow(log.close);
//        }
//
//        /**
//         * Delete this log segment from the filesystem.
//         * @throws KafkaStorageException if the delete fails.
//         */
//        def delete() {
//            val deletedLog = log.delete();
//            val deletedIndex = index.delete();
//            if(!deletedLog && log.file.exists)
//                throw new KafkaStorageException("Delete of log " + log.file.getName + " failed.");
//            if(!deletedIndex && index.file.exists)
//                throw new KafkaStorageException("Delete of index " + index.file.getName + " failed.");
//        }
//
//        /**
//         * The last modified time of this log segment as a unix time stamp
//         */
//        def lastModified = log.file.lastModified;
//
//        /**
//         * Change the last modified time for this log segment
//         */
//        def lastModified_=(Long ms) = {
//            log.file.setLastModified(ms);
//            index.file.setLastModified(ms);
//        }
//    }
}