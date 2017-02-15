package org.rascalmpl.value.io.binary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

import org.rascalmpl.value.IValue;
import org.rascalmpl.value.IValueFactory;
import org.rascalmpl.value.io.binary.stream.IValueInputStream;
import org.rascalmpl.value.io.binary.stream.IValueOutputStream;
import org.rascalmpl.value.io.binary.stream.IValueOutputStream.CompressionRate;
import org.rascalmpl.value.type.TypeStore;


/**
 * Experimental wrapper class for serializable IValues.
 * Your mileage may vary due to use of the BinaryWriter.
 */
public class SerializableValue<T extends IValue> implements Serializable {
	private static final long serialVersionUID = -5507315290306212326L;
	private IValueFactory vf;
	private T value;
	
	public SerializableValue(IValueFactory vf, T value, TypeStore store) {
		this.vf = vf;
		this.value = value;
	}
	
	public T getValue() {
		return value;
	}
	
	public void write(OutputStream out) throws IOException {
		new ObjectOutputStream(out).writeObject(this);
	}
	
	@SuppressWarnings("unchecked")
	public static <U extends IValue> SerializableValue<U> read(InputStream in) throws IOException {
		try {
			return (SerializableValue<U>) new ObjectInputStream(in).readObject();
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		} 
	}

	private void writeObject(java.io.ObjectOutputStream out) throws IOException {
		String factoryName = vf.getClass().getName();
		out.write("factory".getBytes());
		out.write(':');
		out.writeInt(factoryName.length());
		out.write(':');
		out.write(factoryName.getBytes("UTF8"));
		out.write(':');
		ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
		try (IValueOutputStream writer = new IValueOutputStream(bytesStream, CompressionRate.Normal)) {
		    writer.write(value);
		}
		byte[] bytes = bytesStream.toByteArray();
		out.writeInt(bytes.length);
		out.write(':');
		out.write(bytes);
	}

	@SuppressWarnings("unchecked")
	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		try {
			in.read(new byte["factory".length()], 0, "factory".length());
			in.read(); // ':'
			int length = in.readInt();
			in.read(); // ':'
			byte[] factoryName = new byte[length];
			in.read(factoryName, 0, length);
			in.read(); // ':'
			int amountOfBytes = in.readInt();
			in.read(); // ':'
			byte[] bytes = new byte[amountOfBytes];
			in.read(bytes);
			Class<?> clazz = getClass().getClassLoader().loadClass(new String(factoryName, "UTF8"));
			this.vf = (IValueFactory) clazz.getMethod("getInstance").invoke(null, new Object[0]);
			try (IValueInputStream reader = new IValueInputStream(new ByteArrayInputStream(bytes), vf)) {
			    this.value = (T) reader.read();
			}
		}
		catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException | NoSuchMethodException | SecurityException | ClassCastException e) {
			throw new IOException("Could not load IValueFactory", e);
		}
	}
}
