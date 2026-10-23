/*
 * Copyright (C) 2023, Bjørn D. Rasmussen, BearWare.dk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#ifndef MOVEUSERSDLG_H
#define MOVEUSERSDLG_H

#include <QDialog>
#include <QListWidget>
#include <QComboBox>
#include <QHBoxLayout>
#include <QVBoxLayout>
#include <QPushButton>
#include <QLabel>
#include <QVector>

class MoveUsersDlg : public QDialog
{
    Q_OBJECT

public:
    explicit MoveUsersDlg(const QVector<int>& userIds,
                          const QVector<int>& channelIds,
                          const QVector<int>& preselectedUserIds = {},
                          int preselectedChannelId = 0,
                          QWidget *parent = nullptr);
    QVector<int> getSelectedUserIds() const;
    int getSelectedChannelId() const;

protected:
    bool eventFilter(QObject *watched, QEvent *event) override;

private slots:
    void slotMoveClicked();
    void slotItemChanged(QListWidgetItem* item);

private:
    QListWidget* usersListWidget;
    QListWidgetItem* selectAllItem;
    bool m_updatingCheckStates;
    QComboBox* channelComboBox;
    QPushButton* moveBtn;
    QPushButton* cancelBtn;
    QVector<int> userIds;
    QVector<int> channelIds;

    void setupUi();
    void populateUsers(const QVector<int>& preselectedUserIds);
    void populateChannels(int preselectedChannelId);
};

#endif // MOVEUSERSDLG_H
