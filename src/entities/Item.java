package entities;

import java.io.Serializable;
import javax.persistence.*;


/**
 * The persistent class for the items database table.
 * 
 */
@Entity
@Table(name="items")
@NamedQuery(name="Item.findAll", query="SELECT i FROM Item i")
public class Item implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private int itemID;

	private String descrip;

	//bi-directional many-to-one association to User
	@ManyToOne(cascade={CascadeType.ALL})
	@JoinColumn(name="username")
	private User user;

	public Item() {
	}

	public int getItemID() {
		return this.itemID;
	}

	public void setItemID(int itemID) {
		this.itemID = itemID;
	}

	public String getDescrip() {
		return this.descrip;
	}

	public void setDescrip(String descrip) {
		this.descrip = descrip;
	}

	public User getUser() {
		return this.user;
	}

	public void setUser(User user) {
		this.user = user;
	}

}