package Main;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.FIPAException;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.core.AID;

public class BookBuyerAgent extends Agent {

	private String targetBookTitle;

	// The list of known seller agents
	private AID[] sellerAgents;

	protected void setup() {
		System.out.println("BookBuyerAgent " + getLocalName() + " is starting...");

		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			targetBookTitle = (String) args[0];
			System.out.println("BookBuyerAgent " + getLocalName() + " is trying to buy " + targetBookTitle);

			addBehaviour(new TickerBehaviour(this, 30000) { 
				protected void onTick() {

					System.out.println("Try to buy "+ targetBookTitle);
					// Update the list of seller agents
					DFAgentDescription template = new DFAgentDescription();
					ServiceDescription sd = new ServiceDescription();
					sd.setType("book-selling-agent");
					template.addServices(sd);
					try {
						DFAgentDescription[] result = DFService.search(myAgent, template); 
						if(result.length == 0) {
							System.out.println("BookBuyerAgent " + getLocalName() + " did not find any seller agents");
						}else {
							System.out.println("BookBuyerAgent " + getLocalName() + " found the following seller agents: ");
							sellerAgents = new AID[result.length];
							for (int i = 0; i < result.length; ++i) {
								sellerAgents[i] = result[i].getName();
								System.out.println(sellerAgents[i].getName());
							}
						}
					}
					catch (FIPAException fe) {
						fe.printStackTrace();
					}
					myAgent.addBehaviour(new RequestPerformer());
				}
			} 
			);
		}
		else {
			System.out.println("BookBuyerAgent " + getLocalName() + "can not find book name");
			doDelete();
		}
	}

	protected void takeDown() {
		System.out.println("BookBuyerAgent " + getLocalName() +" is terminating...");
	}

	private class RequestPerformer extends Behaviour {
		private static final long serialVersionUID = 1L;

		private AID bestSeller; // The agent who provides the best offer 
		private int bestPrice;  // The best offered price
		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// Send the cfp to all sellers
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < sellerAgents.length; ++i) {
					cfp.addReceiver(sellerAgents[i]);
				} 
				cfp.setContent(targetBookTitle);
				cfp.setConversationId("book-trade");
				cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// Receive all proposals/refusals from seller agents
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer 
						int price = Integer.parseInt(reply.getContent());
						if (bestSeller == null || price < bestPrice) {
							// This is the best offer at present
							bestPrice = price;
							bestSeller = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= sellerAgents.length) {
						// We received all replies
						step = 2; 
					}
				}
				else {
					block();
				}
				break;
			case 2:
				// Send the purchase order to the seller that provided the best offer
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(targetBookTitle);
				order.setConversationId("book-trade");
				order.setReplyWith("order"+System.currentTimeMillis());
				myAgent.send(order);

				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(
						MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:      
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
				if (reply != null) {
					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Purchase successful. We can terminate
						System.out.println(targetBookTitle
								+ " successfully purchased from agent "
								+ reply.getSender().getName());
						System.out.println("Price = " + bestPrice);
						myAgent.doDelete();
					}
					else {
						System.out.println("Attempt failed: requested book already sold.");
					}

					step = 4;
				}
				else {
					block();
				}
				break;
			}        
		}

		public boolean done() {
			if (step == 2 && bestSeller == null) {
				System.out.println("Attempt failed: "+targetBookTitle+" not available for sale");
			}
			return ((step == 2 && bestSeller == null) || step == 4);
		}
	}  // End of inner class RequestPerformer

}


