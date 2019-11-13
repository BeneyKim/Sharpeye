package sharpeye.sharpeye.signs.frontManagers;

import android.content.Context;
import android.graphics.Color;

import sharpeye.sharpeye.data.SharedPreferencesHelper;
import sharpeye.sharpeye.signs.frontViews.IFrontViews;
import sharpeye.sharpeye.utils.CurrentState;
import sharpeye.sharpeye.utils.Font;

/**
 * holds the logic associated with the speed textView
 */
public class SpeedViewManager extends FrontElementManager {

    /**
     * called at the object creation
     *
     * @param _frontViews Object extended from IFrontView
     */
    public SpeedViewManager(Context _context, IFrontViews _frontViews, Boolean _isVisible) {
        super(_context, _frontViews, _isVisible);
        frontViews.setFont(Font.FontList.CHARACTERE);
    }

    /**
     * called for updating the front element
     * @param currentState instance of current state object
     */
    @Override
    public void update(CurrentState currentState) {
        boolean pref = getSharedPreferences();
        if (isVisible) {
            if (!pref) {
                frontViews.setInvisible();
                isVisible = false;
            }
        }
        else {
            if (pref) {
                frontViews.setVisible();
                isVisible = true;
            }
        }
        if (currentState.getGPSenabled() && currentState.getGPSPermission()) {
            frontViews.setText(String.valueOf((int)currentState.getSpeed()) + "Km/H");
            if (currentState.getSpeed() > currentState.getSpeedLimit()) {
                frontViews.setTextColor(Color.rgb(255, 0, 0));
            } else if (currentState.getSpeed() >= (currentState.getSpeedLimit() * 0.95)) {
                frontViews.setTextColor(Color.rgb(255, 165, 0));
            } else {
                frontViews.setTextColor(Color.rgb(255, 255, 255));
            }
        }
    }

    /**
     * @return A boolean value if/not the feature is allowed
     */
    private Boolean getSharedPreferences()
    {
        return SharedPreferencesHelper.INSTANCE.getSharedPreferencesBoolean(context, "speed_display", false);
    }
}
