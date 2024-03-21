package com.deshaw.hypercube;

// Recreate with `cog -rc FloatFromIntegerHypercube.java`
// [[[cog
//     import cog
//     import numpy
//     import primitive_from_primitive_hypercube
//
//     cog.outl(primitive_from_primitive_hypercube.generate(numpy.float32, numpy.int32))
// ]]]
import java.util.Map;

/**
 * A float {@link Hypercube} which is a view of a int
 * one that casts values from one type to another.
 *
 * <p>The casting follows Java language semantics meaning null values may not be
 * preserved.
 */
public class FloatFromIntegerHypercube
    extends AbstractFloatHypercube
    implements FloatHypercube
{
    /**
     * The hypercube which we wrap.
     */
    private IntegerHypercube myHypercube;

    // ----------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param hypercube  The hypercube to cast to/from.
     *
     * @throws IllegalArgumentException If there was any problem with the arguments.
     * @throws NullPointerException     If a {@code null} pointer was
     *                                  encountered.
     */
    public FloatFromIntegerHypercube(final IntegerHypercube hypercube)
        throws IllegalArgumentException,
               NullPointerException
    {
        super(hypercube.getDimensions());

        myHypercube = hypercube;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Float getObjectAt(final long index)
        throws IndexOutOfBoundsException
    {
        final Integer obj = myHypercube.getObjectAt(index);
        return (obj == null) ? null : (float)(obj.intValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setObjectAt(final long index, final Float value)
        throws IndexOutOfBoundsException
    {
        myHypercube.setObjectAt(
            index,
            (value == null) ? null : (int)(value.floatValue())
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getAt(final long index)
        throws IndexOutOfBoundsException
    {
        return (float)(myHypercube.getAt(index));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAt(final long index, final float value)
        throws IndexOutOfBoundsException
    {
        myHypercube.setAt(index, (int)(value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float get(final long... indices)
        throws IndexOutOfBoundsException
    {
        return (float)(myHypercube.get(indices));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(final float value, final long... indices)
        throws IndexOutOfBoundsException
    {
        myHypercube.set((int)(value), indices);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String,Boolean> createFlags()
    {
        final Map<String,Boolean> result = super.createFlags();
        result.put("aligned",      false);
        result.put("behaved",      false);
        result.put("c_contiguous", false);
        result.put("owndata",      false);
        result.put("writeable",    true);
        return result;
    }
}

// [[[end]]] (checksum: 2b035c43b7c7d3dd47f98b81a7916675)
