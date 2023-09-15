package com.deshaw.hypercube;

// Recreate with `cog.py -rc Long2dWrappingHypercube.java`
// [[[cog
//     import cog
//     import numpy
//     import primitive_nd_wrapping_hypercube
//
//     cog.outl(primitive_nd_wrapping_hypercube.generate(2, numpy.int64))
// ]]]
import java.util.Map;

/**
 * A 2-dimensional hypercube which has {@code long} values
 * as its elements and is backed by a user-supplied array.
 */
public class Long2dWrappingHypercube
    extends AbstractLongHypercube
{
    /**
     * The array(s) which we wrap.
     */
    private final long[][] myElements;

    /**
     * Get the {@link Dimension}s of the given array.
     */
    private static Dimension<?>[] getDimensions(final long[][] array)
    {
        final long[] shape = new long[2];
        shape[0] = Math.max(shape[0], array.length);
        for (long[] array1 : array) {
            shape[1] = Math.max(shape[1], array1.length);
        }
        return Dimension.of(shape);
    }

    /**
     * Constructor.
     */
    public Long2dWrappingHypercube(final long[][] array)
        throws IllegalArgumentException,
               NullPointerException
    {
        super(getDimensions(array));

        myElements = array;
    }

    /**
     * {@inheritDoc}
     *
     * Attempts to get values where the underlying array data is missing will yield
     * the result of {@code 0L}.
     */
    @Override
    public long get(final long... indices)
        throws IndexOutOfBoundsException
    {
        if (indices == null) {
            throw new NullPointerException("Given null indices");
        }
        if (indices.length != 2) {
            throw new IndexOutOfBoundsException(
                "Wanted 2 indices but had " + indices.length
            );
        }
        final long index1 = indices[1];
        if (index1 < 0 || index1 >= length(1)) {
            throw new IndexOutOfBoundsException(
                "Bad index[1]: " + index1
            );
        }
        final long index0 = indices[0];
        if (index0 < 0 || index0 >= length(0)) {
            throw new IndexOutOfBoundsException(
                "Bad index[0]: " + index0
            );
        }

        if (index0 >= myElements.length) {
            return 0L;
        }
        final long[] array1 = myElements[(int)index0];

        if (index1 >= array1.length) {
            return 0L;
        }

        return array1[(int)index1];
    }

    /**
     * {@inheritDoc}
     *
     * Attempts to set values where the underlying array capacity is too small
     * will be silently ignored.
     */
    @Override
    public void set(final long value, final long... indices)
        throws IndexOutOfBoundsException
    {
        if (indices == null) {
            throw new NullPointerException("Given null indices");
        }
        if (indices.length != 2) {
            throw new IndexOutOfBoundsException(
                "Wanted 2 indices but had " + indices.length
            );
        }
        final long index1 = indices[1];
        if (index1 < 0 || index1 >= length(1)) {
            throw new IndexOutOfBoundsException(
                "Bad index[1]: " + index1
            );
        }
        final long index0 = indices[0];
        if (index0 < 0 || index0 >= length(0)) {
            throw new IndexOutOfBoundsException(
                "Bad index[0]: " + index0
            );
        }

        if (index0 >= myElements.length) {
            return;
        }
        final long[] array1 = myElements[(int)index0];

        if (index1 >= array1.length) {
            return;
        }

        array1[(int)index1] = value;
    }

    /**
     * {@inheritDoc}
     *
     * Attempts to get values where the underlying array is missing will yield
     * the result of {@code 0L}.
     */
    @Override
    public long getAt(long index)
        throws IndexOutOfBoundsException
    {
        if (index < 0 || index >= size) {{
            throw new IndexOutOfBoundsException(
                "Bad index: " + index
            );
        }}
        final long index1 = (index % length(1)); index /= length(1);
        final long index0 = index;

        if (index0 >= myElements.length) {
            return 0L;
        }
        final long[] array1 = myElements[(int)index0];

        if (index1 >= array1.length) {
            return 0L;
        }

        return array1[(int)index1];
    }

    /**
     * {@inheritDoc}
     *
     * Attempts to set values where the underlying array capacity is too small
     * will be silently ignored.
     */
    @Override
    public void setAt(long index, final long value)
        throws IndexOutOfBoundsException
    {
        if (index < 0 || index >= size) {{
            throw new IndexOutOfBoundsException(
                "Bad index: " + index
            );
        }}
        final long index1 = (index % length(1)); index /= length(1);
        final long index0 = index;

        if (index0 >= myElements.length) {
            return;
        }
        final long[] array1 = myElements[(int)index0];

        if (index1 >= array1.length) {
            return;
        }

        array1[(int)index1] = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getObjectAt(final long index)
        throws IndexOutOfBoundsException
    {
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
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Boolean> createFlags()
    {
        final Map<String,Boolean> result = super.createFlags();
        result.put("aligned",   true );
        result.put("owndata",   false);
        result.put("writeable", true );
        return result;
    }
}

// [[[end]]] (checksum: cca087376de562831a81f131e97d314f)
