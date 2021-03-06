package kafka.log;/**
 * Created by zhoulf on 2017/4/11.
 */

import kafka.annotation.nonthreadsafe;
import kafka.utils.Logging;
import kafka.utils.Prediction;
import kafka.utils.Utils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * @author
 * @create 2017-04-11 05 11
 **/
public interface OffsetMap {
    Integer slots();

    void put(ByteBuffer key, Long offset) throws UnsupportedEncodingException;

    Long get(ByteBuffer key) throws UnsupportedEncodingException;

    void clear();

    Integer size();

    default Double utilization() {
        return size().doubleValue() / slots();
    }
}


/**
 * An hash table used for deduplicating the log. This hash table uses a cryptographicly secure hash of the key as a proxy for the key
 * for comparisons and to save space on object overhead. Collisions are resolved by probing. This hash table does not support deletes.
 *
 */
@nonthreadsafe
class SkimpyOffsetMap extends Logging implements OffsetMap {
    public Integer memory;
    public String hashAlgorithm;

    @Override
    public String loggerName() {
        return SkimpyOffsetMap.class.getName();
    }

    /**
     *
     * @param memory        The amount of memory this map can use
     * @param hashAlgorithm The hash algorithm instance to MD2 use, MD5, SHA-1, SHA-256, SHA-384, SHA-512
     */
    public SkimpyOffsetMap(Integer memory, String hashAlgorithm) {
        this.memory = memory;
        this.hashAlgorithm = hashAlgorithm;
        if (hashAlgorithm == null)
            hashAlgorithm = "MD5";
        try {
            digest = MessageDigest.getInstance(hashAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            error(e.getMessage(),e);
        }
    }

    private ByteBuffer bytes = ByteBuffer.allocate(memory);

    /* the hash algorithm instance to use, defualt is MD5 */
    private MessageDigest digest;

    /* the number of bytes for this hash algorithm */
    private Integer hashSize = digest.getDigestLength();

    /* create some hash buffers to avoid reallocating each time */
    private byte[] hash1 = new byte[hashSize];
    private byte[] hash2 = new byte[hashSize];

    /* number of entries put into the map */
    private Integer entries = 0;

    /* number of lookups on the map */
    private Long lookups = 0L;

    /* the number of probes for all lookups */
    private Long probes = 0L;

    /**
     * The number of bytes of space each entry uses (the number of bytes in the hash plus an 8 byte offset)
     */
    public Integer bytesPerEntry = hashSize + 8;

    /**
     * The maximum number of entries this map can contain
     */
    @Override
    public Integer slots(){
        return (memory / bytesPerEntry);
    }


    /**
     * Associate this offset to the given key.
     *
     * @param key    The key
     * @param offset The offset
     */
    @Override
    public void put(ByteBuffer key, Long offset) {
        Prediction.require(entries < slots(), "Attempt to add a new entry to a full offset map.");
        lookups += 1;
        try {
            hashInto(key, hash1);
            // probe until we find the first empty slot;
            int attempt = 0;
            Integer pos = positionOf(hash1, attempt);
            while (!isEmpty(pos)) {
                bytes.position(pos);
                bytes.get(hash2);
                if (Arrays.equals(hash1, hash2)) {
                    // we found an existing entry, overwrite it and return (size does not change);
                    bytes.putLong(offset);
                    return;
                }
                attempt += 1;
                pos = positionOf(hash1, attempt);
            }
            // found an empty slot, update it--size grows by 1;
            bytes.position(pos);
            bytes.put(hash1);
            bytes.putLong(offset);
            entries += 1;
        } catch (DigestException e) {
            error(e.getMessage(),e);
        }

    }

/**
 * Check that there is no entry at the given position
 */
    private  Boolean isEmpty(Integer position){
        return bytes.getLong(position)==0&&bytes.getLong(position +8)==0&&bytes.getLong(position +16)==0;
    }

    /**
     * Get the offset associated with this key.
     * @param key The key
     * @return The offset associated with this key or -1 if the key is not found
     */
    @Override
    public Long get(ByteBuffer key) {
        lookups += 1;
        try {
            hashInto(key, hash1);
            // search for the hash of this key by repeated probing until we find the hash we are looking for or we find an empty slot;
            Integer attempt = 0;
            Integer pos = 0;
            do {
                pos = positionOf(hash1, attempt);
                bytes.position(pos);
                if (isEmpty(pos))
                    return -1L;
                bytes.get(hash2);
                attempt += 1;
            } while (!Arrays.equals(hash1, hash2));
            bytes.getLong();
        } catch (DigestException e) {
            error(e.getMessage(),e);
        }
        return -1L;
    }

    /**
     * Change the salt used for key hashing making all existing keys unfindable.
     * Doesn't actually zero out the array.
     */
    @Override
    public void clear() {
        this.entries = 0;
        this.lookups = 0L;
        this.probes = 0L;
        Arrays.fill(bytes.array(), bytes.arrayOffset(), bytes.arrayOffset() + bytes.limit(), new Byte("0"));
    }

    /**
     * The number of entries put into the map (note that not all may remain)
     */
    @Override public Integer  size(){
        return entries;
    }

    /**
     * The rate of collisions in the lookups
     */
    public Double collisionRate(){
        return (this.probes -this.lookups)/this.lookups.doubleValue();
    }


    /**
     * Calculate the ith probe position. We first try reading successive integers from the hash itself
     * then if all of those fail we degrade to linear probing.
     *
     * @param hash    The hash of the key to find the position for
     * @param attempt The ith probe
     * @return The byte offset in the buffer at which the ith probing for the given hash would reside
     */
    private Integer positionOf(byte[] hash, Integer attempt) {
        Integer probe = Utils.readInt(hash, Math.min(attempt, hashSize - 4)) + Math.max(0, attempt - hashSize + 4);
        Integer slot = Math.abs(probe) % slots();
        this.probes += 1;
        return slot * bytesPerEntry;
    }

    /**
     * The offset at which we have stored the given key
     *
     * @param key    The key to hash
     * @param buffer The buffer to store the hash into
     */
    private  void hashInto(ByteBuffer key, byte[] buffer) throws DigestException {
        key.mark();
        digest.update(key);
        key.reset();
        digest.digest(buffer, 0, hashSize);
    }

}
