package com.deshaw.hypercube;

// Recreate with `cog -rc FloatArrayHypercube.java`
// [[[cog
//     import cog
//     import numpy
//     import primitive_array_hypercube
//
//     cog.outl(primitive_array_hypercube.generate(numpy.float32))
// ]]]
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Level;

/**
 * A hypercube which has float values as its elements and stores them
 * in a 1D array of effectively infinite size.
 */
public class FloatArrayHypercube
    extends AbstractFloatHypercube
{
    /**
     * An empty array of floats.
     */
    private static final float[] EMPTY = new float[0];

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
    private final float[][] myElements;

    /**
     * The first array in myElements. This is optimistically here to avoid an
     * extra hop through memory for accesses to smaller cubes.
     */
    private final float[] myElements0;

    /**
     * Constructor.
     */
    public FloatArrayHypercube(final Dimension<?>[] dimensions)
        throws IllegalArgumentException,
               NullPointerException
    {
        super(dimensions);

        int numArrays = (int)(size >>> MAX_ARRAY_SHIFT);
        if (numArrays * MAX_ARRAY_SIZE < size) {
            numArrays++;
        }

        myElements = new float[numArrays][];
        for (int i=0; i < myElements.length; i++) {
            final float[] elements = allocForIndex(i);
            Arrays.fill(elements, Float.NaN);
            myElements[i] = elements;
        }
        myElements0 = (myElements.length == 0) ? EMPTY : myElements[0];
    }

    /**
     * Constructor.
     */
    @SuppressWarnings("unchecked")
    public FloatArrayHypercube(final Dimension<?>[] dimensions,
                                       final List<Float> elements)
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
        myElements = new float[numArrays][];
        for (int i=0; i < numArrays; i++) {
            myElements[i] = allocForIndex(i);
        }
        myElements0 = (myElements.length == 0) ? EMPTY : myElements[0];

        // Populate
        for (int i=0; i < elements.size(); i++) {
            final Float value = elements.get(i);
            myElements[(int)(i >>> MAX_ARRAY_SHIFT)][(int)(i & MAX_ARRAY_MASK)] =
                (value == null) ? Float.NaN
                                : value.floatValue();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fill(final float v)
    {
        for (int i=0; i < myElements.length; i++) {
            Arrays.fill(myElements[i], v);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toFlattenedObjs(final long srcPos,
                                final Float[] dst,
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
            final float[] array = myElements[(int)(pos >>> MAX_ARRAY_SHIFT)];
            final float d = array[(int)(pos & MAX_ARRAY_MASK)];
            dst[dstPos + i] = Float.valueOf(d);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromFlattenedObjs(final Float[] src,
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
            float[] array = myElements[idx];
            final Float value = src[srcPos + i];
            array[(int)(pos & MAX_ARRAY_MASK)] =
                (value == null) ? Float.NaN : value.floatValue();
        }
        postWrite();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toFlattened(final long      srcPos,
                            final float[] dst,
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
            final float[] array = myElements[startIdx];
            switch (length) {
            case 0:
                // NOP
                break;

            case 1:
                // Just the one element
                dst[dstPos] = array[(int)(srcPos & MAX_ARRAY_MASK)];
                break;

            default:
                // Standard copy within the same sub-array
                System.arraycopy(array, (int)(srcPos & MAX_ARRAY_MASK),
                                 dst, dstPos,
                                 length);
            }
        }
        else {
            // Split into two copies
            final float[] startArray = myElements[startIdx];
            final float[] endArray   = myElements[  endIdx];
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromFlattened(final float[] src,
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
            float[] array = myElements[startIdx];

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
            float[] startArray = myElements[startIdx];
            float[] endArray   = myElements[  endIdx];

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
    public void copyFrom(final FloatArrayHypercube that)
    {
        if (that == null) {
            throw new IllegalArgumentException("Given a null cube to copy from");
        }
        if (!matches(that)) {
            throw new IllegalArgumentException("Given cube is not compatible");
        }

        for (int i=0; i < myElements.length; i++) {
            System.arraycopy(that.myElements[i], 0,
                             this.myElements[i], 0,
                             that.myElements[i].length);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float weakGet(final long... indices)
        throws IndexOutOfBoundsException
    {
        return weakGetAt(toOffset(indices));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void weakSet(final float d, final long... indices)
        throws IndexOutOfBoundsException
    {
        weakSetAt(toOffset(indices), d);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Float weakGetObjectAt(final long index)
        throws IndexOutOfBoundsException
    {
        return Float.valueOf(weakGetAt(index));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void weakSetObjectAt(final long index, final Float value)
        throws IndexOutOfBoundsException
    {
        weakSetAt(index, (value == null) ? Float.NaN : value.floatValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float weakGetAt(final long index)
        throws IndexOutOfBoundsException
    {
        if (index < MAX_ARRAY_SIZE) {
            return myElements0[(int)index];
        }
        else {
            final float[] array = myElements[(int)(index >>> MAX_ARRAY_SHIFT)];
            return array[(int)(index & MAX_ARRAY_MASK)];
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void weakSetAt(final long index, final float value)
        throws IndexOutOfBoundsException
    {
        if (index < MAX_ARRAY_SIZE) {
            myElements0[(int)index] = value;
        }
        else {
            float[] array = myElements[(int)(index >>> MAX_ARRAY_SHIFT)];
            array[(int)(index & MAX_ARRAY_MASK)] = value;
        }
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
    private float[] allocForIndex(final int index)
    {
        // The last array in the list of arrays might not be an exact multiple
        // of MAX_ARRAY_SIZE so we look to account for that. We compute its
        // length as the 'tail' value.
        final long tail = (size & MAX_ARRAY_MASK);
        final int  sz   = (tail == 0 || index+1 < myElements.length)
                              ? (int)MAX_ARRAY_SIZE
                              : (int)tail;
        return new float[sz];
    }
}

// [[[end]]] (checksum: 1f0774f203dc9b40d99fd4dd672b653d)
