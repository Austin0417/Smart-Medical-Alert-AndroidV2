package com.example.smartmedicalalert.recycleradapter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartmedicalalert.interfaces.IDeviceConnect;
import com.example.smartmedicalalert.MyBluetoothDevice;
import com.example.smartmedicalalert.R;

import java.util.List;

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder> {
    private List<MyBluetoothDevice> dataset;


    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final CardView cardView;

        public ViewHolder(View view) {
            super(view);
            cardView = view.findViewById(R.id.cardView);
        }

        public CardView getCardView() { return cardView; }
    }

    public RecyclerAdapter(List<MyBluetoothDevice> dataset) {
        this.dataset = dataset;
    }

    public void updateDataset(List<MyBluetoothDevice> dataset) {
        this.dataset = dataset;
        notifyDataSetChanged();
    }

    @Override
    public RecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.devices_card_view, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        CardView cardView = viewHolder.getCardView();
        TextView deviceName = cardView.findViewById(R.id.deviceName);
        TextView deviceInfo = cardView.findViewById(R.id.deviceInfo);
        BluetoothDevice device = dataset.get(viewHolder.getAdapterPosition()).getDevice();
        List<ParcelUuid> uuids = dataset.get(viewHolder.getAdapterPosition()).getUuids();

        String deviceTypeString = "Device Type: ";
        int deviceType = device.getType();
        switch(deviceType) {
            case BluetoothDevice.DEVICE_TYPE_LE:
                deviceTypeString += "BLE";
                break;
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                deviceTypeString += "Classic";
                break;
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                deviceTypeString += "Dual";
                break;
            default:
                deviceTypeString += "Unknown";
                break;
        }

        String uuidInfo = "";
        if (uuids != null && !uuids.isEmpty()) {
            for (ParcelUuid uuid : uuids) {
                uuidInfo += "\t" + uuid.toString() + "\n";
            }
        }
        deviceName.setText(device.getName());
        deviceInfo.setText("Device Address: " + device.getAddress()
                + "\n" + deviceTypeString
                + "\nService UUIDS: " + uuidInfo);

        cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog dialog = new AlertDialog.Builder(cardView.getContext())
                        .setTitle("Connect to Device")
                        .setMessage("Connect to device with name " + device.getName() + "?")
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ((IDeviceConnect) cardView.getContext()).onDeviceConnect(viewHolder.getAdapterPosition());
                            }
                        })
                        .create();
                dialog.show();
            }
        });
    }

    @Override
    public int getItemCount() { return dataset.size(); }
}
