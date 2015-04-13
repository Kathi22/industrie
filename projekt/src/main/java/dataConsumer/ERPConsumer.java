package dataConsumer;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import com.thoughtworks.xstream.XStream;

import dataSupplier.Types;
import de.dhbw.mannheim.erpsim.model.CustomerOrder;
import de.dhbw.mannheim.erpsim.model.MachineOrder;

public class ERPConsumer extends Consumer
{
	EPServiceProvider epService;
	String exchangeName;
	XStream xstream = new XStream();

	public ERPConsumer(String exchangeName) throws IOException
	{
		super(exchangeName);
		this.exchangeName = exchangeName;
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();
		channel.exchangeDeclare(exchangeName, "fanout");
		String queueName = channel.queueDeclare().getQueue();
		channel.queueBind(queueName, exchangeName, "");

		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

		this.setConsumer(new QueueingConsumer(channel));
		channel.basicConsume(queueName, true, this.getConsumer());
		epService = EPServiceProviderManager.getDefaultProvider();
		String expression = "select quality from dataSupplier.Types.win:time(30 sec)";
		EPStatement statement = epService.getEPAdministrator().createEPL(
				expression);

		Listener listener = new Listener();
		statement.addListener(listener);
	}

	@Override
	public void process(String message) throws JAXBException
	{
		if (this.exchangeName == "CUSTOMER_ORDER_QUEUE")
		{
			//JAXBContext customerContext = JAXBContext.newInstance(CustomerOrder.class);
			//Unmarshaller um = customerContext.createUnmarshaller();
			CustomerOrder co = (CustomerOrder) xstream.fromXML(message);
					//um.unmarshal(new StreamSource(new StringReader(message)));
			epService.getEPRuntime().sendEvent(co);
		}
		else if (this.exchangeName == "MACHINE_ORDER_QUEUE")
		{
			//JAXBContext machineContext = JAXBContext.newInstance(MachineOrder.class);
			//Unmarshaller um = machineContext.createUnmarshaller();
			MachineOrder mo = (MachineOrder) xstream.fromXML(message);
					//(MachineOrder) um.unmarshal(new StreamSource(new StringReader(message)));
			epService.getEPRuntime().sendEvent(mo);
		}
		
	}

	@Override
	public void receive() throws JAXBException
	{
		while (true)
		{
			QueueingConsumer.Delivery delivery;
			try
			{
				delivery = this.getConsumer().nextDelivery();
				String message = new String(delivery.getBody());
				System.out.println(" [x] Received '" + message + "'");
				process(message);
			}
			catch (ShutdownSignalException e)
			{
				e.printStackTrace();
			}
			catch (ConsumerCancelledException e)
			{
				e.printStackTrace();
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}

		}
	}

}