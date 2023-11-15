package Main;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class BookSellerAgent extends Agent {

	// The catalogue of books for sale (maps the title of a book to its price)
	private Hashtable catalogue;
	// The GUI by means of which the user can add books in the catalogue
	private BookSellerGui guiInstance;

	// Put agent initializations here
	protected void setup() {
		// Create the catalogue
		catalogue = new Hashtable();
		// Create and show the GUI
		guiInstance = new BookSellerGui(this);
		guiInstance.showGui();
		
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("book-selling-agent");
		sd.setName("JADE-book-trading");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		// Add the behaviour serving requests for offer from buyer agents
		addBehaviour(new OfferRequestsServer());

		// Add the behaviour serving purchase orders from buyer agents
		addBehaviour(new PurchaseOrdersServer());
	}
	// Put agent clean-up operations here
	protected void takeDown() {
		// Close the GUI
		guiInstance.dispose();
		// Printout a dismissal message
		System.out.println("BookSellerAgent " + getLocalName() +" is terminating...");
	}
	/**
	 This is invoked by the GUI when the user adds a new book for sale
	 */
	public void updateCatalogue(final String title, final int price) {
		addBehaviour(new OneShotBehaviour() {
			public void action() { 
				catalogue.put(title, new Integer(price));
				System.out.println(getName() + " inserted book " + title + " into catalogue with price = " + price);
				}
		} );
	}

	private class PurchaseOrdersServer extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {

				String title = msg.getContent();
				ACLMessage reply = msg.createReply();
				Integer price = (Integer) catalogue.remove(title);
				if (price != null) {
					reply.setPerformative(ACLMessage.INFORM);
					System.out.println(title + " sold to BookBuyerAgent " + msg.getSender().getName());
				}
				else {
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}

	private class OfferRequestsServer extends CyclicBehaviour {
		private static final long serialVersionUID = 1L;

		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();

				Integer price = (Integer) catalogue.get(title);
				if (price != null) {
					// The requested book is available for sale. Reply with the price
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent(String.valueOf(price.intValue()));
				}
				else {
					// The requested book is NOT available for sale.
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("not-available");
				}
				myAgent.send(reply);
			}
			else {
				block();
			}
		}
	}
}