package com.deshaw.hypercube;

// Recreate with `cog -rc LongArrayHypercube.java`
// [[[cog
//     import cog
//     import numpy
//     import primitive_array_hypercube
//
//     cog.outl(primitive_array_hypercube.generate(numpy.int64))
// ]]]
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;

/**
 * A hypercube which has long values as its elements and stores them
 * in a 1D array of effectively infinite size.
 */
public class LongArrayHypercube
    extends AbstractLongHypercube
{
    /**
     * The shift for the max array size.
     */
    private static final int MAX_ARRAY_SHIFT = 30;

    /**
     * The largest array size.
     */
    private static final long MAX_ARRAY_SIZE = (1L << MAX_ARRAY_SHIFT);

    /**
     * The mask for array index tweaking.
     */
    private static final long MAX_ARRAY_MASK = MAX_ARRAY_SIZE - 1;

    /**
     * The array-of-arrays of elements which we hold. We have multiple arrays
     * since we might have a size which is larger than what can be represented
     * by a single array. (I.e. more than 2^30 elements.)
     */
    private final AtomicReferenceArray<long[]> myElements;

    /**
     * Constructor.
     */
    public LongArrayHypercube(final Dimension<?>[] dimensions)
        throws IllegalArgumentException,
               NullPointerException
    {
        super(dimensions);

        int numArrays = (int)(size >>> MAX_ARRAY_SHIFT);
        if (numArrays * MAX_ARRAY_SIZE < size) {
            numArrays++;
        }

        myElements = new AtomicReferenceArray<>(numArrays);
        for (int i=0; i < myElements.length(); i++) {
            final long[] elements = allocForIndex(i);
            Arrays.fill(elements, 0L);
            myElements.set(i, elements);
        }
    }

    /**
     * Constructor.
     */
    @SuppressWarnings("unchecked")
    public LongArrayHypercube(final Dimension<?>[] dimensions,
                                       final List<Long> elements)
        throws IllegalArgumentException,
               NullPointerException
    {
        super(dimensions);

        if (elements.size() != size) {
            throw new IllegalArgumentException(
                "Number of elements, " + elements.size() + ", " +
                "does not match expected size, " + size + " " +
                "for dimensions " + Arrays.toString(dimensions)
            );
        }

        int numArrays = (int)(size >>> MAX_ARRAY_SHIFT);
        if (numArrays * MAX_ARRAY_SIZE < size) {
            numArrays++;
        }
        myElements = new AtomicReferenceArray<>(numArrays);
        for (int i=0; i < numArrays; i++) {
            myElements.set(i, allocForIndex(i));
        }

        // There will never be more elements than MAX_ARRAY_SIZE so all these
        // will fit in the first one.
        assert(elements.size() <= MAX_ARRAY_SIZE);
        for (int i=0; i < elements.size(); i++) {
            final Long value = elements.get(i);
            myElements.get(0)[i] = (value == null) ? 0L
                                                   : value.longValue();
       }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fill(final long v)
    {
        for (int i=0; i < myElements.length(); i++) {
            long[] elements = myElements.get(i);
            if (elements == null) {
                elements = allocForIndex(i);
                myElements.set(i, elements);
            }
            Arrays.fill(elements, v);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toFlattenedObjs(final long srcPos,
                                final Long[] dst,
                                final int dstPos,
                                final int length)
        throws IllegalArgumentException,
               IndexOutOfBoundsException,
               UnsupportedOperationException
    {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest(
                "Flattening with " +
                "srcPos=" + srcPos + " dst=" + dst + " dstPos=" + dstPos + " " +
                "length=" + length
            );
        }

        // Check the arguments
        checkFlattenArgs(srcPos, dst, dstPos, length);

        preRead();
        for (int i=0; i < length; i++) {
            final long pos = srcPos + i;
            final long[] array = myElements.get((int)(pos >>> MAX_ARRAY_SHIFT));
            final long d =
                (array == null) ? 0L : array[(int)(pos & MAX_ARRAY_MASK)];
            dst[dstPos + i] = Long.valueOf(d);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromFlattenedObjs(final Long[] src,
                                  final int srcPos,
                                  final long dstPos,
                                  final int length)
        throws IllegalArgumentException,
               IndexOutOfBoundsException,
               NullPointerException
    {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest(
                "Unflattening with " +
                "src=" + src + " srcPos=" + srcPos + " dstPos=" + dstPos + " " +
                "length=" + length
            );
        }

        // Check input
        checkUnflattenArgs(srcPos, dstPos, length);
        if (src == null) {
            throw new NullPointerException("Given a null array");
        }
        if (src.length - srcPos < length) {
            throw new IndexOutOfBoundsException(
                "Source position, " + srcPos + ", " +
                "plus length ," + length + ", " +
                "was greater than the array size, " + src.length
            );
        }

        // Safe to copy in
        for (int i=0; i < length; i++) {
            final long pos = dstPos + i;
            final int  idx = (int)(pos >>> MAX_ARRAY_SHIFT);
            long[] array = myElements.get(idx);
            if (array == null) {
                array = allocForIndex(idx);
                myElements.set(idx, array);
            }
            final Long value = src[srcPos + i];
            array[(int)(pos & MAX_ARRAY_MASK)] =
                (value == null) ? 0L : value.longValue();
        }
        postWrite();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toFlattened(final long      srcPos,
                            final long[] dst,
                            final int       dstPos,
                            final int       length)
        throws IllegalArgumentException,
               IndexOutOfBoundsException,
               UnsupportedOperationException
    {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest(
                "Flattening with " +
                "srcPos=" + srcPos + " dst=" + dst + " dstPos=" + dstPos + " " +
                "length=" + length
            );
        }

        // Check the arguments
        checkFlattenArgs(srcPos, dst, dstPos, length);

        // These can never differ by more than 1 since length is an int. And the
        // end is non-inclusive since we're counting fence, not posts.
        preRead();
        final int startIdx = (int)((srcPos             ) >>> MAX_ARRAY_SHIFT);
        final int endIdx   = (int)((srcPos + length - 1) >>> MAX_ARRAY_SHIFT);
        if (startIdx == endIdx) {
            // What to copy? Try to avoid the overhead of the system call. If we are
            // striding through the cube then we may well have just the one.
            final long[] array = myElements.get(startIdx);
            switch (length) {
            case 0:
                // NOP
                break;

            case 1:
                // Just the one element
                dst[dstPos] =
                    (array == null) ? 0L : array[(int)(srcPos & MAX_ARRAY_MASK)];
                break;

            default:
                // Standard copy within the same sub-array
                if (array != null) {
                    System.arraycopy(
                        array, (int)(srcPos & MAX_ARRAY_MASK),
                        dst, dstPos,
                        length
                    );
                }
                else {
                    Arrays.fill(dst, dstPos, dstPos + length, 0L);
                }
            }
        }
        else {
            // Split into two copies
            final long[] startArray = myElements.get(startIdx);
            final long[] endArray   = myElements.get(  endIdx);
            if (startArray != null && endArray != null) {
                final int startPos    = (int)(srcPos & MAX_ARRAY_MASK);
                final int startLength = length - (startArray.length - startPos);
                final int endLength   = length - startLength;
                System.arraycopy(startArray, startPos,
                                 dst,        dstPos,
                                 startLength);
                System.arraycopy(endArray,   0,
                                 dst,        dstPos + startLength,
                                 endLength);
            }
            else {
                Arrays.fill(dst, dstPos, dstPos + length, 0L);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromFlattened(final long[] src,
                              final int       srcPos,
                              final long      dstPos,
                              final int       length)
        throws IllegalArgumentException,
               IndexOutOfBoundsException,
               NullPointerException
    {
        if (LOG.isLoggable(Level.FINEST)) {
            LOG.finest(
                "Unflattening with " +
                "src=" + src + " srcPos=" + srcPos + " dstPos=" + dstPos + " " +
                "length=" + length
            );
        }

        // Sanitise input
        checkUnflattenArgs(srcPos, dstPos, length);
        if (src == null) {
            throw new NullPointerException("Given a null array");
        }

        // These can never differ by more than 1 since length is an int
        final int startIdx = (int)((dstPos             ) >>> MAX_ARRAY_SHIFT);
        final int endIdx   = (int)((dstPos + length - 1) >>> MAX_ARRAY_SHIFT);

        // What to copy? Try to avoid the overhead of the system call. If we are
        // striding through the cube then we may well have just the one.
        if (startIdx == endIdx) {
            // Get the array, creating if needbe
            long[] array = myElements.get(startIdx);
            if (array == null) {
                array = allocForIndex(startIdx);
                myElements.set(startIdx, array);
            }

            // And handle it
            switch (length) {
            case 0:
                // NOP
                break;

            case 1:
                // Just the one element
                if (srcPos >= src.length) {
                    throw new IndexOutOfBoundsException(
                        "Source position, " + srcPos + ", " +
                        "plus length ," + length + ", " +
                        "was greater than the array size, " + src.length
                    );
                }
                array[(int)(dstPos & MAX_ARRAY_MASK)] = src[srcPos];
                break;

            default:
                // Standard copy within the same sub-array
                if (src.length - srcPos < length) {
                    throw new IndexOutOfBoundsException(
                        "Source position, " + srcPos + ", " +
                        "plus length ," + length + ", " +
                        "was greater than the array size, " + src.length
                    );
                }
                System.arraycopy(
                    src, srcPos,
                    array, (int)(dstPos & MAX_ARRAY_MASK),
                    length
                );
                break;
            }
        }
        else {
            // Split into two copies
            long[] startArray = myElements.get(startIdx);
            long[] endArray   = myElements.get(  endIdx);
            if (startArray == null) {
                startArray = allocForIndex(startIdx);
                myElements.set(startIdx, startArray);
            }
            if (endArray == null) {
                endArray = allocForIndex(endIdx);
                myElements.set(endIdx, endArray);
            }

            // And do the copy
            final int startPos    = (int)(dstPos & MAX_ARRAY_MASK);
            final int startLength = length - (startArray.length - startPos);
            final int endLength   = length - startLength;

            System.arraycopy(src,        srcPos,
                             startArray, startPos,
                             startLength);
            System.arraycopy(src,        srcPos + startLength,
                             endArray,   0,
                             endLength);
        }
        postWrite();
    }

    /**
     * Copy the contents of given cube into this one.
     *
     * @throws IllegalArgumentException if the given cube was not compatible for
     *                                  some reason.
     */
    public void copyFrom(final LongArrayHypercube that)
    {
        if (that == null) {
            throw new IllegalArgumentException("Given a null cube to copy from");
        }
        if (!matches(that)) {
            throw new IllegalArgumentException("Given cube is not compatible");
        }

        // We always expect this to be true but, just in case something really
        // weird is going on, we fall back to the superclass's method. This
        // override is really just an optimisation anyhow.
        if (myElements.length() == that.myElements.length()) {
            for (int i=0; i < myElements.length(); i++) {
                final long[] els = that.myElements.get(i);
                if (els == null) {
                    myElements.set(i, null);
                }
                else {
                    myElements.set(i, Arrays.copyOf(els, els.length));
                }
            }
        }
        else {
            super.copyFrom((LongHypercube)that);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long get(final long... indices)
        throws IndexOutOfBoundsException
    {
        return getAt(toOffset(indices));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(final long d, final long... indices)
        throws IndexOutOfBoundsException
    {
        setAt(toOffset(indices), d);
        postWrite();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getObjectAt(final long index)
        throws IndexOutOfBoundsException
    {
        preRead();
        return Long.valueOf(getAt(index));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setObjectAt(final long index, final Long value)
        throws IndexOutOfBoundsException
    {
        setAt(index, (value == null) ? 0L : value.longValue());
        postWrite();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getAt(final long index)
        throws IndexOutOfBoundsException
    {
        preRead();
        final long[] array = myElements.get((int)(index >>> MAX_ARRAY_SHIFT));
        return (array == null) ? 0L : array[(int)(index & MAX_ARRAY_MASK)];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAt(final long index, final long value)
        throws IndexOutOfBoundsException
    {
        final int idx = (int)(index >>> MAX_ARRAY_SHIFT);
        long[] array = myElements.get(idx);
        if (array == null) {
            array = allocForIndex(idx);
            myElements.set(idx, array);
        }
        array[(int)(index & MAX_ARRAY_MASK)] = value;
        postWrite();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Boolean> createFlags()
    {
        final Map<String,Boolean> result = super.createFlags();
        result.put("aligned",      true);
        result.put("behaved",      true);
        result.put("c_contiguous", true);
        result.put("owndata",      true);
        result.put("writeable",    true);
        return result;
    }

    /**
     * Allocate an array for the given myElements index.
     */
    private long[] allocForIndex(final int index)
    {
        // The last array in the list of arrays might not be an exact multiple
        // of MAX_ARRAY_SIZE so we look to account for that. We compute its
        // length as the 'tail' value.
        final long tail = (size & MAX_ARRAY_MASK);
        final int  sz   = (tail == 0 || index+1 < myElements.length())
                              ? (int)MAX_ARRAY_SIZE
                              : (int)tail;
        return new long[sz];
    }
}

// [[[end]]] (checksum: 75b9bf9c134058e46f2a3f264a4e32ba)
