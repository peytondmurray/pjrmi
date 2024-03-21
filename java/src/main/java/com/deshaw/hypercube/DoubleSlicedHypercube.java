package com.deshaw.hypercube;

// Recreate with `cog -rc DoubleSlicedHypercube.java`
// [[[cog
//     import cog
//     import numpy
//     import primitive_sliced_hypercube
//
//     cog.outl(primitive_sliced_hypercube.generate(numpy.float64))
// ]]]
import com.deshaw.hypercube.Dimension.Accessor;
import com.deshaw.hypercube.Dimension.Coordinate;
import com.deshaw.hypercube.Dimension.Slice;
import com.deshaw.hypercube.Dimension.Roll;
import com.deshaw.pjrmi.PJRmi.GenericReturnType;

import java.util.Map;
import java.util.logging.Level;

/**
 * A sub-cube which is a sliced view of another double-based
 * {@link Hypercube}.
 */
public class DoubleSlicedHypercube
    extends SlicedHypercube<Double>
    implements DoubleHypercube
{
    // Public members which look like numpy.ndarray ones

    /**
     * The transpose of this cube.
     */
    public final DoubleHypercube T;

    // -----------------------------------------------------------------------

    /**
     * The hypercube which we wrap.
     */
    private DoubleHypercube myHypercube;

    // ----------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param hypercube  The hypercube to slice.
     * @param accessors  How to slice this cube. The number of slices must match
     *                   the number of dimensions of this {@link Hypercube}.
     *                   However, a slice may be {@code null} if it does not
     *                   apply.
     *
     * @throws DimensionalityException  If the slices did not match the
     *                                  {@code hypercube}'s dimensions.
     * @throws IllegalArgumentException If there was any other problem with the
     *                                  arguments.
     * @throws NullPointerException     If a {@code null} pointer was
     *                                  encountered.
     */
    public DoubleSlicedHypercube(final DoubleHypercube hypercube,
                                        final Accessor<?>[] accessors)
        throws DimensionalityException,
               IllegalArgumentException,
               NullPointerException
    {
        super(hypercube, accessors);

        T = new DoubleTransposedHypercube(this);
        myHypercube = hypercube;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @GenericReturnType
    public DoubleHypercube slice(final Dimension.Accessor<?>... accessors)
        throws DimensionalityException,
               IllegalArgumentException,
               NullPointerException
    {
        return new DoubleSlicedHypercube(this, accessors);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @GenericReturnType
    public DoubleHypercube roll(final Dimension.Roll<?>... rolls)
        throws DimensionalityException,
               IllegalArgumentException,
               NullPointerException
    {
        return new DoubleAxisRolledHypercube(this, rolls);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @GenericReturnType
    public DoubleHypercube transpose()
    {
        return this.T;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toFlattened(final long      srcPos,
                            final double[] dst,
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

        // The lowest dimension of the cube which we're wrapping, and the
        // accessor for it
        final int         dim      = getWrapped().getNDim() - 1;
        final Accessor<?> accessor = getAccessor(dim);

        // Index arrays, working space
        final long[] local   = new long[             ndim];
        final long[] wrapped = new long[getWrapped().getNDim()];

        // We start at srcPos and walk to srcPos+length. We do this in steps
        // such that we copy as much of the lowest dimension as possible at a
        // time.
        for (int pos = 0; pos < length; /*incr below*/) {
            // Determine the wrapped indices from the current position
            getWrappedIndices(srcPos + pos, local, wrapped);

            // Figure out the offset within the wrapped cube
            final long wrappedSrcPos = getWrapped().toOffset(wrapped);

            // Now figure out how far we want to walk within the wrapped cube
            final int  left = length - pos;
            final long dimLeft;
            if (accessor == null) {
                // No restriction, go from the index to the end of the dimension
                dimLeft = Math.min(left,
                                   getWrapped().length(dim) - wrapped[dim]);
            }
            else if (accessor instanceof Slice) {
                // Go from the index to the end of the slice
                final Slice<?> slice = (Slice<?>)accessor;
                dimLeft = Math.min(left, slice.end() - wrapped[dim]);
            }
            else if (accessor instanceof Coordinate) {
                // A coordinate means just the single value
                dimLeft = 1;
            }
            else {
                throw new IllegalArgumentException(
                    "Unhandled Accessor type: " + accessor
                );
            }

            // This should fit into an int given the logic above. If it's not
            // then something has gone wrong.
            assert(dimLeft <= Integer.MAX_VALUE);

            // Now we simply flatten from the cube into the result array
            myHypercube.toFlattened(wrappedSrcPos,
                                    dst,
                                    dstPos + pos,
                                    (int)dimLeft);

            // And step forward over what we copied
            pos += dimLeft;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fromFlattened(final double[] src,
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

        // Sanitise input
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

        // The lowest dimension of the cube which we're wrapping, and the
        // accessor for it
        final int         dim      = getWrapped().getNDim() - 1;
        final Accessor<?> accessor = getAccessor(dim);

        // Index arrays, working space
        final long[] local   = new long[             ndim];
        final long[] wrapped = new long[getWrapped().getNDim()];

        // We start at srcPos and walk to srcPos+length. We do this in steps
        // such that we copy as much of the lowest dimension as possible at a
        // time.
        for (int pos = 0; pos < length; /*incr below*/) {
            // Determine the wrapped indices from the current position
            getWrappedIndices(dstPos + pos, local, wrapped);

            // Figure out the offset within the wrapped cube
            final long wrappedDstPos = getWrapped().toOffset(wrapped);

            // Now figure out how far we want to walk within the wrapped cube
            final int  left = length - pos;
            final long dimLeft;
            if (accessor == null) {
                // No restriction, go from the index to the end of the dimension
                dimLeft = Math.min(left,
                                   getWrapped().length(dim) - wrapped[dim]);
            }
            else if (accessor instanceof Slice) {
                // Go from the index to the end of the slice
                final Slice<?> slice = (Slice<?>)accessor;
                dimLeft = Math.min(left, slice.end() - wrapped[dim]);
            }
            else if (accessor instanceof Coordinate) {
                // A coordinate means just the single value
                dimLeft = 1;
            }
            else {
                throw new IllegalArgumentException(
                    "Unhandled Accessor type: " + accessor
                );
            }

            // This should fit into an int given the logic above. If it's not
            // then something has gone wrong.
            assert(dimLeft <= Integer.MAX_VALUE);

            // Now we simply flatten from the cube into the result array
            myHypercube.fromFlattened(src,
                                      srcPos + pos,
                                      wrappedDstPos,
                                      (int)dimLeft);

            // And step forward over what we copied
            pos += dimLeft;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double get(final long... indices)
        throws IndexOutOfBoundsException
    {
        return getAt(toOffset(indices));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(final double value, final long... indices)
        throws IndexOutOfBoundsException
    {
        setAt(toOffset(indices), value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getAt(final long index)
        throws IndexOutOfBoundsException
    {
        // Give it back from the parent
        return myHypercube.get(getWrappedIndices(index));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAt(final long index, final double value)
        throws IndexOutOfBoundsException
    {
        // Set it in the parent
        myHypercube.set(value, getWrappedIndices(index));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void assignFrom(final Object object)
        throws IllegalArgumentException
    {
        DoubleHypercube.super.assignFrom(object);
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
        result.put("owndata",      true);
        result.put("writeable",    true);
        return result;
    }
}

// [[[end]]] (checksum: 41f7595aad2581c7492a35b4255d5827)
