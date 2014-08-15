package thack.ac.cardsafe;

/**
 * Helper class for passing multiple parameters in Async task
 * Created by paradite on 11/8/14.
 */
public class Pair
{
    public String id;
    public String date;

    /**
     *
     * @param id    ID of the card
     * @param date  Date
     */
    public Pair(String id, String date){
        this.id = id;
        this.date = date;
    }
}
