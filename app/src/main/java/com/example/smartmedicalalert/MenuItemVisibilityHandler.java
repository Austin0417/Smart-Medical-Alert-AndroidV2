package com.example.smartmedicalalert;

import android.view.MenuItem;

import com.example.smartmedicalalert.interfaces.MenuItemListener;

public class MenuItemVisibilityHandler {
    private MenuItem menuItem;
    private MenuItemListener listener;

    public MenuItemVisibilityHandler(MenuItem menuItem, MenuItemListener listener) {
        this.menuItem = menuItem;
        this.listener = listener;
    }
    public MenuItemVisibilityHandler(MenuItem menuItem) {
        this.menuItem = menuItem;
    }
    public void setMenuItemVisibilityListener(MenuItemListener listener) {
        this.listener = listener;
    }

    public void setMenuItemVisibility(boolean visibility) {
        menuItem.setVisible(visibility);
    }
}
