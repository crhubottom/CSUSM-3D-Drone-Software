package com.example.airsimapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

public class CommandAdapter extends RecyclerView.Adapter<CommandAdapter.CommandViewHolder> {
    private ArrayList<AutopilotCommand> commandList;
    private OnItemClickListener listener;
    public interface OnItemClickListener {
        void onItemClick(int position);
    }


    public CommandAdapter(ArrayList<AutopilotCommand> commandList, OnItemClickListener listener) {
        this.commandList = commandList;
        this.listener = listener;
    }

    public static class CommandViewHolder extends RecyclerView.ViewHolder {
        TextView commandText;
        TextView commandText2;
        TextView commandText3;
        TextView commandText4;
        ImageView commandIcon;

        public CommandViewHolder(View itemView, OnItemClickListener listener) {

            super(itemView);
            commandText = itemView.findViewById(R.id.field1TextView);
            commandIcon = itemView.findViewById(R.id.commandImageView);
            commandText2 = itemView.findViewById(R.id.field2TextView);
            commandText3 = itemView.findViewById(R.id.field3TextView);
            commandText4 = itemView.findViewById(R.id.field4TextView);
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(position);
                }
            });
        }
    }

    public CommandAdapter(ArrayList<AutopilotCommand> commandList) {
        this.commandList = commandList;
    }

    @NonNull
    @Override
    public CommandViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_command, parent, false);
        return new CommandViewHolder(view, listener);


    }

    @Override
    public void onBindViewHolder(@NonNull CommandViewHolder holder, int position) {
        AutopilotCommand command = commandList.get(position);

        // First, hide all optional fields; we’ll show the ones we need
        holder.commandText.setVisibility(View.GONE);
        holder.commandText2.setVisibility(View.GONE);
        holder.commandText3.setVisibility(View.GONE);
        holder.commandText4.setVisibility(View.GONE);

        switch (command.getId()) {
            case "Heading&Speed":
                HeadingAndSpeed hs = (HeadingAndSpeed) command;
                holder.commandIcon.setImageResource(R.drawable.heading);

                // Field 1 = desired heading
                holder.commandText.setText(
                        String.format(Locale.getDefault(), "%.1f°", hs.getDesiredHeading())
                );
                holder.commandText.setVisibility(View.VISIBLE);

                // Field 2 = desired speed
                holder.commandText2.setText(
                        String.format(Locale.getDefault(), "%.1f m/s", hs.getDesiredSpeed())
                );
                holder.commandText2.setVisibility(View.VISIBLE);

                // Field 4 = end time HH:mm
                holder.commandText4.setText(
                        String.format(Locale.getDefault(), "%02d%02d",
                                hs.getHourEndTime(), hs.getMinuteEndTime())
                );
                holder.commandText4.setVisibility(View.VISIBLE);
                break;

            case "GPS":
                GPSCommand gps = (GPSCommand) command;
                holder.commandIcon.setImageResource(R.drawable.gps);

                // Field 1 = latitude
                holder.commandText.setText(
                        String.format(Locale.getDefault(), "%.5f°", gps.getLatitude())
                );
                holder.commandText.setVisibility(View.VISIBLE);

                // Field 2 = longitude
                holder.commandText2.setText(
                        String.format(Locale.getDefault(), "%.5f°", gps.getLongitude())
                );
                holder.commandText2.setVisibility(View.VISIBLE);

                // Field 3 = altitude
                holder.commandText3.setText(
                        String.format(Locale.getDefault(), "%.1f m", gps.getAltitude())
                );
                holder.commandText3.setVisibility(View.VISIBLE);

                // Field 4 = end time HH:mm
                holder.commandText4.setText(
                        String.format(Locale.getDefault(), "%02d:%02d",
                                gps.getHourEndTime(), gps.getMinuteEndTime())
                );
                holder.commandText4.setVisibility(View.VISIBLE);
                break;

            case "LoiterPattern":
                LoiterPattern lp = (LoiterPattern) command;
                holder.commandIcon.setImageResource(R.drawable.loiter);

                // Field 1 = pattern name
                holder.commandText.setText(lp.getPatternType());
                holder.commandText.setVisibility(View.VISIBLE);

                // Field 2 = end time HHmm
                holder.commandText2.setText(
                        String.format(Locale.getDefault(), "%02d:%02d",
                                lp.getHourEndTime(), lp.getMinuteEndTime())
                );
                holder.commandText2.setVisibility(View.VISIBLE);
                break;

            default:
                holder.commandIcon.setImageResource(R.drawable.gps);
                // You can choose to show something generic here or leave it blank
                break;
        }
    }

    @Override
    public int getItemCount() {
        return commandList.size();
    }

    public void updateCommands(ArrayList<AutopilotCommand> newCommands) {
        commandList = newCommands;
        notifyDataSetChanged();
    }
}

