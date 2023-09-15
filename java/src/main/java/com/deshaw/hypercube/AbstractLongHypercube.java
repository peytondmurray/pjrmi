package com.deshaw.hypercube;

// Recreate with `cog.py -rc AbstractLongHypercube.java`
// [[[cog
//     import cog
//     import numpy
//     import abstract_primitive_hypercube
//
//     cog.outl(abstract_primitive_hypercube.generate(numpy.int64))
// ]]]
import com.deshaw.hypercube.Dimension.Coordinate;
import com.deshaw.pjrmi.PJRmi.GenericReturnType;

/**
 * The base class for all long-based hypercubes.
 */
public abstract class AbstractLongHypercube
    extends AbstractHypercube<Long>
    implements LongHypercube
{
    // Public members which look like numpy.ndarray ones

    /**
     * The transposed view of this cube.
     */
    public final LongHypercube T;

    /**
     * The number of bytes required to store the hypercube's data.
     */
    public final long nbytes;

    // -------------------------------------------------------------------------

    /**
     * Constructor.
     */
    protected AbstractLongHypercube(final Dimension<?>[] dimensions)
        throws IllegalArgumentException,
               NullPointerException
    {
        super(dimensions, Long.class);

        T = new LongTransposedHypercube(this);
        nbytes = size * itemsize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @GenericReturnType
    public LongHypercube slice(final Dimension.Accessor<?>... accessors)
        throws DimensionalityException,
               IllegalArgumentException,
               NullPointerException
    {
        return new LongSlicedHypercube(this, accessors);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @GenericReturnType
    public LongHypercube rollFlat(final long shift)
    {
        // Check for the NOP case
        if (getSize()         == 0 ||
            shift % getSize() == 0)
        {
            return this;
        }
        // Otherwise return a flat-rolled hypercube
        else {
            return new LongFlatRolledHypercube(this, shift);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @GenericReturnType
    public LongHypercube roll(final Dimension.Roll<?>... rolls)
        throws DimensionalityException,
               IllegalArgumentException,
               NullPointerException
    {
        if (rolls == null) {
            throw new NullPointerException("Given a null array of rolls");
        }

        // Check the lengths are compatible, Any missing Roll will be treated
        // as an unconstrained roll later on.
        if (rolls.length != getNDim()) {
            throw new DimensionalityException(
                "Number of rolls, " + rolls.length + ", didn't match " +
                "number of dimensions " + getNDim()
            );
        }

        // A flag to check for NOP (all zero/null) rolls.
        boolean isNop = true;

        for (int i=0; i < rolls.length; i++) {
            if (rolls[i] != null && !dim(i).equals(rolls[i].getDimension())) {
                throw new DimensionalityException(
                    "Dimension of roll[" + i + "], " +
                    rolls[i].getDimension() + ", didn't match " +
                    "hypercube dimension " + dim(i)
                );
            }
            else if (rolls[i] != null && rolls[i].shift() != 0) {
                isNop = false;
            }
        }

        // Simply return the original hypercube if all the rolls are NOP.
        if (isNop) {
            return this;
        }

        return new LongAxisRolledHypercube(this, rolls);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @GenericReturnType
    public LongHypercube transpose()
    {
        return this.T;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long get(final Coordinate<?>... coordinates)
        throws DimensionalityException
    {
        // Make sure that they match dimensionally
        if (coordinates == null) {
            throw new NullPointerException("Given null coordinates");
        }
        if (coordinates.length != ndim) {
            throw new DimensionalityException(
                "Number of coordinates, " + coordinates.length + ", " +
                "did not match number of dimensions, " + ndim
            );
        }

        // Handle the common cases
        switch (coordinates.length) {
        case 1:
            dimensionCheck(coordinates[0], 0);
            return get(coordinates[0].get());

        case 2:
            dimensionCheck(coordinates[0], 0);
            dimensionCheck(coordinates[1], 1);
            return get(coordinates[0].get(),
                       coordinates[1].get());

        case 3:
            dimensionCheck(coordinates[0], 0);
            dimensionCheck(coordinates[1], 1);
            dimensionCheck(coordinates[2], 2);
            return get(coordinates[0].get(),
                       coordinates[1].get(),
                       coordinates[2].get());

        case 4:
            dimensionCheck(coordinates[0], 0);
            dimensionCheck(coordinates[1], 1);
            dimensionCheck(coordinates[2], 2);
            dimensionCheck(coordinates[3], 3);
            return get(coordinates[0].get(),
                       coordinates[1].get(),
                       coordinates[2].get(),
                       coordinates[3].get());

        case 5:
            dimensionCheck(coordinates[0], 0);
            dimensionCheck(coordinates[1], 1);
            dimensionCheck(coordinates[2], 2);
            dimensionCheck(coordinates[3], 3);
            dimensionCheck(coordinates[4], 4);
            return get(coordinates[0].get(),
                       coordinates[1].get(),
                       coordinates[2].get(),
                       coordinates[3].get(),
                       coordinates[4].get());
        }

        // Higher dimensions. Transform to the given indices.
        final long[] indices = new long[coordinates.length];
        for (int i=0; i < coordinates.length; i++) {
            final Coordinate<?> coordinate = coordinates[i];
            dimensionCheck(coordinate, i);
            indices[i] = coordinate.get();
        }

        // And hand off
        return get(indices);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(final long value, final Coordinate<?>... coordinates)
        throws DimensionalityException
    {
        // Make sure that they match dimensionally
        if (coordinates == null) {
            throw new NullPointerException("Given null coordinates");
        }
        if (coordinates.length != ndim) {
            throw new DimensionalityException(
                "Number of coordinates, " + coordinates.length + ", " +
                "did not match number of dimensions, " + ndim
            );
        }

        // Handle the common cases
        switch (coordinates.length) {
        case 1:
            dimensionCheck(coordinates[0], 0);
            set(value,
                coordinates[0].get());
            break;

        case 2:
            dimensionCheck(coordinates[0], 0);
            dimensionCheck(coordinates[1], 1);
            set(value,
                coordinates[0].get(),
                coordinates[1].get());
            break;

        case 3:
            dimensionCheck(coordinates[0], 0);
            dimensionCheck(coordinates[1], 1);
            dimensionCheck(coordinates[2], 2);
            set(value,
                coordinates[0].get(),
                coordinates[1].get(),
                coordinates[2].get());
            break;

        case 4:
            dimensionCheck(coordinates[0], 0);
            dimensionCheck(coordinates[1], 1);
            dimensionCheck(coordinates[2], 2);
            dimensionCheck(coordinates[3], 3);
            set(value,
                coordinates[0].get(),
                coordinates[1].get(),
                coordinates[2].get(),
                coordinates[3].get());
            break;

        case 5:
            dimensionCheck(coordinates[0], 0);
            dimensionCheck(coordinates[1], 1);
            dimensionCheck(coordinates[2], 2);
            dimensionCheck(coordinates[3], 3);
            dimensionCheck(coordinates[4], 4);
            set(value,
                coordinates[0].get(),
                coordinates[1].get(),
                coordinates[2].get(),
                coordinates[3].get(),
                coordinates[4].get());
            break;
        }

        // Higher dimensions. Transform to the given indices.
        final long[] indices = new long[coordinates.length];
        for (int i=0; i < coordinates.length; i++) {
            final Coordinate<?> coordinate = coordinates[i];
            dimensionCheck(coordinate, i);
            indices[i] = coordinate.get();
        }

        // And hand off
        set(value, indices);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSet(final long... indices)
        throws IndexOutOfBoundsException
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String toString(final int    dim,
                              final long[] indices)
    {
        final StringBuilder sb = new StringBuilder();
        if (dim > 0 && indices[dim-1] > 0) {
            sb.append('\n');
            for (int i=0; i < dim; i++) {
                sb.append("  ");
            }
        }
        sb.append("[ ");
        for (int i=0; i < length(dim); i++) {
            indices[dim] = i;
            if (i > 0) {
                sb.append(", ");
            }
            if (dim == indices.length-1) {
                sb.append(get(indices));
            }
            else {
                sb.append(toString(dim + 1, indices));
            }
        }
        sb.append(" ]");
        return sb.toString();
    }
}

// [[[end]]] (checksum: 6e8a630eef6f16c8bd268f3bbe14f8ae)
